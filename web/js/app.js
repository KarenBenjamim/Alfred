// Alfred web: login, sincronização em tempo real com o Firestore e as mesmas abas do app.
// Toda a interface é montada com textContent (nada de innerHTML com dados): sem XSS.
import { auth, db, usingEmulator } from './firebase.js';
import {
  onAuthStateChanged, signInWithEmailAndPassword, createUserWithEmailAndPassword,
  sendPasswordResetEmail, GoogleAuthProvider, signInWithPopup, signOut,
} from 'https://www.gstatic.com/firebasejs/13.2.0/firebase-auth.js';
import {
  doc, collection, onSnapshot, setDoc, deleteDoc, deleteField, writeBatch, arrayUnion, arrayRemove, increment,
  waitForPendingWrites, terminate, clearIndexedDbPersistence,
} from 'https://www.gstatic.com/firebasejs/13.2.0/firebase-firestore.js';
import * as S from './schema.js';

// ===================== Utilidades de DOM =====================

const $ = (sel, el = document) => el.querySelector(sel);

/** h('div', {class: 'card', onclick: fn}, 'texto', filho...) — texto sempre como textContent. */
function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'class') el.className = v;
    else if (k === 'style') Object.assign(el.style, v);
    else if (v === true) el.setAttribute(k, '');
    else el.setAttribute(k, v);
  }
  // Cartões clicáveis (role="button") também respondem ao teclado
  if (attrs?.role === 'button' && attrs.onclick) {
    el.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); attrs.onclick(e); }
    });
  }
  for (const c of children.flat(Infinity)) {
    if (c === null || c === undefined || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return el;
}

let toastTimer;
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 3200);
}

/** Frase com o tratamento do perfil ({voc}). */
const voc = (text) => S.honor(text, state.profile?.tratamento ?? S.FORM_NAME, state.profile?.nome ?? '');

// ===================== Estado =====================

const state = {
  user: null,
  profile: undefined, // undefined = carregando; null = ainda sem perfil
  tasks: new Map(), tags: new Map(), meals: new Map(), habits: new Map(), water: new Map(),
  topics: new Map(), modules: new Map(), subjects: new Map(), plan: null,
  tab: 'inicio', topicId: 0, pending: false,
};
let unsubscribe = [];

const today = () => S.isoDay();
const userRef = () => doc(db, S.COL.USERS, state.user.uid);
const ref = (col, id) => doc(db, S.COL.USERS, state.user.uid, col, String(id));

/** Gravação: aparece na hora (cache local) e vai para a nuvem quando houver internet. */
/**
 * Sair: espera até 5 s as gravações pendentes subirem e apaga a cópia offline (IndexedDB) deste
 * navegador, para não deixar os dados da conta no computador. Sem internet, mantém o cache para
 * não perder nada. Recarrega a página no fim (o Firestore encerrado não pode ser reaberto).
 */
async function logout() {
  const sent = await Promise.race([waitForPendingWrites(db).then(() => true), new Promise((r) => setTimeout(() => r(false), 5000))]);
  await signOut(auth).catch(() => {});
  if (sent) {
    try {
      await terminate(db);
      await clearIndexedDbPersistence(db);
    } catch { /* outra aba aberta segura o cache: ele some quando todas fecharem */ }
  }
  location.reload();
}

function write(promise) {
  promise.catch((e) => {
    console.error(e);
    toast(e.code === 'permission-denied' ? 'Sem permissão para gravar.' : 'Não foi possível salvar. Tente de novo.');
  });
}

// ===================== Login =====================

let registerMode = false;

const AUTH_ERRORS = {
  'auth/invalid-email': 'Informe um e-mail válido.',
  'auth/invalid-credential': 'E-mail ou senha incorretos.',
  'auth/wrong-password': 'E-mail ou senha incorretos.',
  'auth/user-not-found': 'E-mail ou senha incorretos.',
  'auth/email-already-in-use': 'Já existe uma conta com este e-mail. Toque em Entrar.',
  'auth/weak-password': 'A senha precisa ter de 8 a 64 caracteres.',
  'auth/too-many-requests': 'Muitas tentativas. Aguarde alguns minutos.',
  'auth/network-request-failed': 'Sem conexão. Verifique a internet e tente de novo.',
  'auth/user-disabled': 'Esta conta foi desativada.',
  'auth/account-exists-with-different-credential': 'Este e-mail já entra por outro método (Google ou senha).',
  'auth/operation-not-allowed': 'Este jeito de entrar não está ativado no projeto. Use outro ou ative no console do Firebase.',
  'auth/popup-closed-by-user': '',
  'auth/cancelled-popup-request': '',
};

function setupLogin() {
  const form = $('#login-form');
  const err = $('#login-error');
  const busy = (v) => form.querySelectorAll('button').forEach((b) => { b.disabled = v; });
  const fail = (e) => {
    busy(false);
    err.textContent = AUTH_ERRORS[e.code] ?? 'Não foi possível entrar agora. Tente de novo.';
  };
  const applyMode = () => {
    $('#login-confirm-label').hidden = !registerMode;
    $('#login-submit').textContent = registerMode ? 'Criar conta' : 'Entrar';
    $('#login-switch').textContent = registerMode ? 'Já tenho conta. Entrar' : 'Ainda não tem conta? Criar conta';
    $('#login-forgot').hidden = registerMode;
    $('#login-password').autocomplete = registerMode ? 'new-password' : 'current-password';
    err.textContent = '';
  };
  if (usingEmulator) $('#login-note').textContent = 'Modo de desenvolvimento: conectado aos emuladores locais do Firebase.';

  $('#login-switch').onclick = () => { registerMode = !registerMode; applyMode(); };
  form.onsubmit = (ev) => {
    ev.preventDefault();
    const email = $('#login-email').value.trim();
    const pass = $('#login-password').value;
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return void (err.textContent = 'Informe um e-mail válido.');
    if (registerMode) {
      if (pass.length < 8 || pass.length > 64) return void (err.textContent = 'A senha precisa ter de 8 a 64 caracteres.');
      if (pass !== $('#login-confirm').value) return void (err.textContent = 'As senhas não conferem.');
    } else if (!pass) {
      return void (err.textContent = 'Campo obrigatório.');
    }
    busy(true);
    (registerMode ? createUserWithEmailAndPassword : signInWithEmailAndPassword)(auth, email, pass)
      .then(() => busy(false), fail);
  };
  $('#login-forgot').onclick = () => {
    const email = $('#login-email').value.trim();
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return void (err.textContent = 'Digite seu e-mail acima primeiro.');
    sendPasswordResetEmail(auth, email).catch(() => {}).finally(() => {
      err.textContent = '';
      toast('Se houver uma conta com esse e-mail, enviei o link para criar uma nova senha.');
    });
  };
  $('#login-google').onclick = () => {
    busy(true);
    signInWithPopup(auth, new GoogleAuthProvider()).then(() => busy(false), fail);
  };
  applyMode();
}

// ===================== Sessão e listeners =====================

onAuthStateChanged(auth, (user) => {
  unsubscribe.forEach((u) => u());
  unsubscribe = [];
  resetData(); // nada do usuário anterior fica na tela (nem decide o onboarding)
  state.user = user;
  $('#boot').hidden = true;
  if (!user) {
    $('#app').hidden = true;
    $('#login').hidden = false;
    return;
  }
  $('#login').hidden = true;
  $('#app').hidden = false;
  listen();
  render();
});

function resetData() {
  for (const k of ['tasks', 'tags', 'meals', 'habits', 'water', 'topics', 'modules', 'subjects']) state[k] = new Map();
  state.plan = null;
  state.profile = undefined;
  state.loadError = '';
  state.topicId = 0;
}

/** Erro ao ouvir o perfil/plano: mostra na tela em vez de "Carregando…" para sempre. */
function loadFailed(what, e) {
  console.error(what, e);
  state.loadError = e.code === 'permission-denied'
    ? 'Sem permissão para ler seus dados. Saia e entre de novo.'
    : 'Não consegui carregar seus dados agora. Verifique a internet.';
  render();
}

function listen() {
  // Ids numéricos (como no app); só a água usa a data como id. Documento estranho é ignorado.
  const sub = (name, key, read, textId = false) => {
    unsubscribe.push(onSnapshot(collection(userRef(), name), { includeMetadataChanges: true }, (snap) => {
      const map = new Map();
      for (const d of snap.docs) {
        const item = read(textId ? d.id : S.parseId(d.id), d.data());
        if (item) map.set(item.id, item);
      }
      state[key] = map;
      syncStatus(snap.metadata);
      render();
    }, (e) => console.error(name, e)));
  };
  sub(S.COL.TASKS, 'tasks', S.readTask);
  sub(S.COL.TAGS, 'tags', S.readTag);
  sub(S.COL.MEALS, 'meals', S.readMeal);
  sub(S.COL.HABITS, 'habits', S.readHabit);
  sub(S.COL.TOPICS, 'topics', S.readTopic);
  sub(S.COL.MODULES, 'modules', S.readModule);
  sub(S.COL.SUBJECTS, 'subjects', S.readSubject);
  sub(S.COL.WATER, 'water', (id, d) => (S.parseDay(String(id)) && typeof d.ml === 'number'
    ? { id: String(id), ml: Math.max(0, Math.min(S.MAX_DAY_ML, d.ml)) } : null), true);
  unsubscribe.push(onSnapshot(doc(userRef(), S.COL.PLAN, 'atual'), (snap) => {
    state.plan = snap.exists() ? snap.data() : null;
    render();
  }, (e) => loadFailed('plano', e)));
  unsubscribe.push(onSnapshot(userRef(), { includeMetadataChanges: true }, (snap) => {
    // Só decide "não tem perfil" quando o servidor confirmar (o cache pode estar vazio)
    if (!snap.exists() && snap.metadata.fromCache) return;
    state.loadError = '';
    state.profile = snap.exists() ? snap.data() : null;
    render();
  }, (e) => loadFailed('perfil', e)));
}

function syncStatus(meta) {
  const el = $('#sync-status');
  if (meta.hasPendingWrites) el.textContent = navigator.onLine ? 'Sincronizando…' : 'Offline · salvo aqui';
  else el.textContent = navigator.onLine ? '' : 'Offline';
}
window.addEventListener('online', () => { $('#sync-status').textContent = ''; });
window.addEventListener('offline', () => { $('#sync-status').textContent = 'Offline'; });

// ===================== Casca: cabeçalho, abas, render =====================

const TABS = [
  ['inicio', 'Início', 'Seu guia, sempre ao seu lado.'],
  ['trabalho', 'Trabalho', 'Compromissos profissionais'],
  ['pessoal', 'Pessoal', 'Tarefas pessoais'],
  ['estudos', 'Estudos', 'Um pouco por dia, {voc}.'],
  ['habitos', 'Hábitos', 'Pequenos hábitos, grandes resultados'],
  ['dieta', 'Dieta', 'Plano alimentar da nutricionista'],
  ['perfil', 'Perfil', 'Seu perfil'],
];

function render() {
  if (!state.user) return;
  const content = $('#content');
  if (state.profile === undefined) {
    content.replaceChildren(state.loadError
      ? h('div', { class: 'card' }, h('p', { class: 'error' }, state.loadError),
        h('div', { class: 'actions' },
          h('button', { class: 'text', onclick: logout }, 'Sair'),
          h('button', { class: 'primary', onclick: () => location.reload() }, 'Tentar de novo')))
      : h('p', { class: 'empty' }, navigator.onLine ? 'Carregando seus dados…'
        : 'Sem internet. Seus dados aparecem assim que a conexão voltar.'));
    return;
  }
  const p = state.profile;
  const onboarding = !p || !p.onboarding;
  $('#tabs').hidden = onboarding;
  const avatar = S.AVATARS[Math.max(0, Math.min(3, p?.avatar ?? 0))];
  $('#header-avatar img').src = avatar;
  $('#header-avatar').onclick = () => go('perfil');
  if (onboarding) {
    $('#header-subtitle').textContent = 'Primeiro acesso';
    content.replaceChildren(renderOnboarding());
    return;
  }
  const tab = TABS.find((t) => t[0] === state.tab) || TABS[0];
  $('#header-subtitle').textContent = voc(tab[2]);
  $('#tabs').replaceChildren(...TABS.filter((t) => t[0] !== 'perfil').map(([id, label]) =>
    h('button', { type: 'button', 'aria-current': id === state.tab ? 'page' : null, onclick: () => go(id) }, label)));
  const views = { inicio: renderHome, trabalho: () => renderTasks('trabalho'), pessoal: () => renderTasks('pessoal'),
    estudos: renderStudy, habitos: renderHabits, dieta: renderDiet, perfil: renderProfile };
  // Não redesenha com um diálogo aberto nem no meio da digitação (atualiza ao fechar/sair do campo)
  if ($('#dialog').open) { state.pending = true; return; }
  const active = document.activeElement;
  if (active && content.contains(active) && active.matches('input:not([type=checkbox]), select, textarea')) {
    if (!state.pending) {
      state.pending = true;
      active.addEventListener('blur', () => { state.pending = false; setTimeout(render, 0); }, { once: true });
    }
    return;
  }
  content.replaceChildren(views[tab[0]]());
}

function go(tab) {
  state.tab = tab;
  render();
  window.scrollTo(0, 0);
}

$('#dialog').addEventListener('close', () => { if (state.pending) { state.pending = false; render(); } });

/** Diálogo com formulário. fields montados por quem chama; onSave devolve false para manter aberto. */
function openDialog(title, body, { onSave, saveLabel = 'Salvar', onDelete } = {}) {
  const dlg = $('#dialog');
  const form = h('form', { method: 'dialog' },
    h('h3', {}, title), body,
    h('div', { class: 'actions' },
      onDelete ? h('button', { type: 'button', class: 'text danger', onclick: () => { dlg.close(); onDelete(); } }, 'Excluir') : null,
      h('span', { class: 'spacer' }),
      h('button', { type: 'button', class: 'text', onclick: () => dlg.close() }, 'Cancelar'),
      onSave ? h('button', { type: 'submit', class: 'primary' }, saveLabel) : null));
  form.onsubmit = (ev) => {
    ev.preventDefault();
    if (onSave && onSave(form) === false) return;
    dlg.close();
  };
  dlg.replaceChildren(form);
  if (!dlg.open) dlg.showModal(); // de um diálogo para outro (ex.: lista de tags -> editar tag)
  form.querySelector('input, select, textarea')?.focus();
}

function confirmDelete(name, message, onConfirm) {
  openDialog(`Excluir "${name}"?`, h('p', { class: 'muted' }, message), { onSave: () => onConfirm(), saveLabel: 'Excluir' });
}

const field = (label, input) => h('label', {}, label, input);

// ===================== Primeiro acesso (onboarding) =====================

function renderOnboarding() {
  const name = h('input', { maxlength: '40', autocomplete: 'given-name', required: true });
  const form = h('select', {}, S.HONORIFICS.map((t, i) => h('option', { value: i }, t)));
  const weight = h('input', { type: 'number', min: '20', max: '400', step: '0.1', inputmode: 'decimal' });
  const avatars = renderAvatarPicker(0);
  const err = h('p', { class: 'error' });
  return h('div', { class: 'card' },
    h('h2', {}, 'Bem-vindo. Um mordomo precisa saber a quem serve.'),
    field('Nome', name), field('Forma de tratamento', form), field('Peso (kg) — para a meta de água', weight),
    h('label', {}, 'Ícone de perfil'), avatars, err,
    h('div', { class: 'actions' }, h('button', { class: 'primary', type: 'button', onclick: () => {
      const n = name.value.trim();
      const kg = parseFloat(String(weight.value).replace(',', '.'));
      if (!n) return void (err.textContent = 'Informe seu nome.');
      if (!(kg >= 20 && kg <= 400)) return void (err.textContent = 'Informe um peso entre 20 e 400 kg.');
      const batch = writeBatch(db);
      batch.set(userRef(), { nome: normalizeName(n), tratamento: Number(form.value), pesoKg: kg,
        avatar: Number(avatars.querySelector('input:checked').value), onboarding: true }, { merge: true });
      // Conta nova pela web também ganha os hábitos sugeridos (como no app)
      if (state.habits.size === 0) {
        HABIT_SUGGESTIONS.forEach((nome, i) => {
          const id = S.newId();
          batch.set(ref(S.COL.HABITS, id), { nome, posicao: i, feitos: [] });
        });
      }
      write(batch.commit());
    } }, 'Concluir')));
}

const HABIT_SUGGESTIONS = ['Beber 2 litros de água', 'Comer 3 porções de frutas ou verduras',
  'Mexer o corpo por 30 minutos', 'Dormir de 7 a 8 horas', '15 minutos sem telas antes de dormir'];

const normalizeName = (n) => n.trim().split(/\s+/).map((w) => w[0].toUpperCase() + w.slice(1)).join(' ');

function renderAvatarPicker(selected) {
  return h('div', { class: 'avatars', role: 'radiogroup' }, S.AVATARS.map((src, i) =>
    h('label', {}, h('input', { type: 'radio', name: 'avatar', value: i, checked: i === selected, 'aria-label': `Ícone ${i + 1}` }),
      h('img', { src, alt: '' }))));
}

// ===================== Início =====================

function greeting() {
  const hr = new Date().getHours();
  const who = S.fullName(state.profile.tratamento, state.profile.nome);
  if (hr < 5) return `Ainda de pé, ${who}? Os morcegos aprovam.`;
  if (hr < 12) return `Bom dia, ${who}.`;
  if (hr < 18) return `Boa tarde, ${who}.`;
  return `Boa noite, ${who}.`;
}

/** Tarefa pendente de maior prioridade (mesmo critério do app). */
function topPending(cat) {
  return [...state.tasks.values()].filter((t) => t.categoria === cat && !S.isClosed(t))
    .sort((a, b) => b.prioridade - a.prioridade || a.dia.localeCompare(b.dia) || a.periodo - b.periodo
      || Number(!a.diaInteiro) - Number(!b.diaInteiro) || a.inicio - b.inicio || a.titulo.localeCompare(b.titulo))[0];
}

function renderHome() {
  const t = today();
  const todayTasks = [...state.tasks.values()].filter((x) => x.dia === t && x.status !== S.STATUS_CANCELED); // cancelada não conta
  const habitsDone = [...state.habits.values()].filter((x) => x.feitos.includes(t)).length;
  const mealsDone = [...state.meals.values()].filter((x) => x.feitas.includes(t)).length;
  const next = (cat, label) => {
    const task = topPending(cat);
    return h('div', { class: 'card', role: 'button', tabindex: '0', onclick: () => go(cat) },
      h('p', { class: 'caption' }, label),
      task ? [h('p', { class: task.dia < t ? 'error' : 'gold' }, task.dia < t ? `Atrasada desde ${S.formatDay(task.dia, t)}`
        : `${S.formatDay(task.dia, t)} · ${whenText(task)}`), h('p', {}, task.titulo)]
        : h('p', { class: 'muted' }, voc(`Nada pendente, {voc}.`)));
  };
  const count = (cat) => {
    const list = todayTasks.filter((x) => x.categoria === cat);
    return `${list.filter((x) => x.status === S.STATUS_DONE).length}/${list.length}`;
  };
  return h('div', {},
    h('div', { class: 'card' }, h('p', { class: 'big' }, greeting()),
      h('p', { class: 'caption' }, new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' }))),
    h('div', { class: 'card' }, h('h3', {}, 'Resumo do dia'),
      h('div', { class: 'stats' },
        stat('Trabalho', count('trabalho')), stat('Pessoal', count('pessoal')),
        stat('Hábitos', `${habitsDone}/${state.habits.size}`), stat('Dieta', `${mealsDone}/${state.meals.size}`))),
    waterCard(),
    h('div', { class: 'grid' }, next('pessoal', 'Próximo compromisso pessoal'), next('trabalho', 'Próximo compromisso de trabalho')));
}

const stat = (label, value) => h('div', {}, h('p', { class: 'caption' }, label), h('p', { class: 'big gold' }, value));

// ===================== Trabalho e Pessoal =====================

/** Tema escuro em uso? (as cores das tags de Trabalho têm um tom para cada tema) */
const isDark = () => {
  const forced = document.documentElement.dataset.theme;
  return forced ? forced === 'dark' : !matchMedia('(prefers-color-scheme: light)').matches;
};

/** "14:00–15:30", "Dia inteiro" ou o período (tarefas sem hora). */
function whenText(task) {
  if (task.diaInteiro) return 'Dia inteiro';
  if (S.hasTime(task)) return task.fim > task.inicio ? `${S.time(task.inicio)}–${S.time(task.fim)}` : S.time(task.inicio);
  return S.PERIODS[task.periodo];
}

/** Mesma ordem do app: dia, período, dia inteiro antes das com hora, hora, prioridade. */
const taskOrder = (a, b) => a.dia.localeCompare(b.dia) || a.periodo - b.periodo
  || Number(!a.diaInteiro) - Number(!b.diaInteiro) || a.inicio - b.inicio || b.prioridade - a.prioridade;

function renderTasks(cat) {
  const t = today();
  const all = [...state.tasks.values()].filter((x) => x.categoria === cat).sort(taskOrder);
  const open = all.filter((x) => !S.isClosed(x));
  const groups = [
    ['Atrasadas', open.filter((x) => x.dia < t)],
    ['Hoje', open.filter((x) => x.dia === t)],
    ['Próximos dias', open.filter((x) => x.dia > t)],
    ['Concluídas e canceladas', all.filter((x) => S.isClosed(x)).reverse().slice(0, 30)],
  ];
  return h('div', {},
    h('div', { class: 'actions' },
      h('button', { class: 'secondary', onclick: () => editTag(null, cat) }, 'Tags'),
      h('button', { class: 'primary', onclick: () => editTask(null, cat) }, '+ Nova tarefa')),
    all.length === 0 ? h('p', { class: 'empty' }, voc('Nenhuma tarefa ainda, {voc}.')) : null,
    groups.filter(([, list]) => list.length).map(([title, list]) =>
      h('section', {}, h('h2', {}, title), h('div', { class: 'card' }, list.map((x) => taskItem(x, t))))));
}

function taskItem(task, t) {
  const done = task.status === S.STATUS_DONE;
  const closed = S.isClosed(task);
  const canceled = task.status === S.STATUS_CANCELED;
  const tags = task.tags.map((id) => state.tags.get(id)).filter(Boolean);
  const dark = isDark();
  return h('div', { class: `item${closed ? ' done' : ''}` },
    tags.length ? h('span', { class: 'stripe', style: { background: S.tagColor(tags[0], dark) } }) : null,
    h('input', { type: 'checkbox', checked: done, 'aria-label': `Concluir ${task.titulo}`, style: { width: '20px', margin: 0 },
      onchange: (e) => write(setDoc(ref(S.COL.TASKS, task.id), { status: e.target.checked ? S.STATUS_DONE : 0 }, { merge: true })) }),
    h('div', { class: 'main', role: 'button', tabindex: '0', onclick: () => editTask(task, task.categoria) },
      h('div', { class: 'title' }, task.titulo),
      task.descricao ? h('div', { class: 'caption' }, task.descricao) : null,
      h('div', { class: 'pills' },
        h('span', { class: `pill${!closed && task.dia < t ? ' overdue' : ''}` }, `${S.formatDay(task.dia, t)} · ${whenText(task)}`),
        task.categoria === 'trabalho' ? h('span', { class: 'pill' }, S.DEMAND_TYPES[task.tipo]) : null,
        h('span', { class: 'pill' }, S.STATUSES[task.status]),
        canceled ? null : h('span', { class: `pill${task.prioridade === 2 ? ' high' : ''}` }, S.PRIORITIES[task.prioridade]),
        task.adiadaDe && !closed ? h('span', { class: 'pill gold' }, `Adiada de ${S.formatDay(task.adiadaDe, t)}`) : null,
        tags.map((tag) => h('span', { class: 'pill', style: { color: S.tagColor(tag, dark) } }, tag.nome)))));
}

function editTask(task, cat) {
  const work = cat === 'trabalho';
  const title = h('input', { maxlength: '120', required: true, value: task?.titulo ?? '' });
  const desc = h('textarea', { maxlength: '1000' }, task?.descricao ?? '');
  const day = h('input', { type: 'date', value: task?.dia ?? today(), required: true });
  const period = h('select', {}, S.PERIODS.slice(0, 3).map((p, i) => h('option', { value: i, selected: i === (task?.periodo ?? 0) }, p)));
  const prio = h('select', {}, S.PRIORITIES.map((p, i) => h('option', { value: i, selected: i === (task?.prioridade ?? 1) }, p)));
  const status = h('select', {}, S.STATUSES.map((p, i) => h('option', { value: i, selected: i === (task?.status ?? 0) }, p)));
  // Trabalho: tipo de demanda e horário (dia inteiro ou início/fim). Tarefa antiga sem hora começa no período dela.
  const type = h('select', {}, S.DEMAND_TYPES.map((p, i) => h('option', { value: i, selected: i === (task?.tipo ?? S.TYPE_OTHER) }, p)));
  const allDay = h('input', { type: 'checkbox', checked: !!task?.diaInteiro });
  const startMin = task && task.inicio >= 0 ? task.inicio : [8 * 60, 13 * 60, 18 * 60][task?.periodo ?? 0];
  const start = h('input', { type: 'time', value: S.time(startMin) });
  const end = h('input', { type: 'time', value: S.time(task && task.fim > startMin ? task.fim : Math.min(24 * 60 - 1, startMin + 60)) });
  const times = h('div', { class: 'row' }, field('Início', start), field('Fim', end));
  times.hidden = allDay.checked;
  allDay.onchange = () => { times.hidden = allDay.checked; };
  const areaTags = [...state.tags.values()].filter((tg) => (tg.grupo >= 0) === work);
  const tagBoxes = h('div', { class: 'pills' }, areaTags.map((tag) =>
    h('label', { class: 'check', style: { color: S.tagColor(tag, isDark()) } },
      h('input', { type: 'checkbox', value: tag.id, checked: task?.tags.includes(tag.id) }), tag.nome)));
  const err = h('p', { class: 'error' });

  /** Lê o formulário; null = inválido (o erro aparece no diálogo). */
  const collect = () => {
    if (!title.value.trim()) { err.textContent = 'Informe o título.'; return null; }
    if (!S.parseDay(day.value)) { err.textContent = 'Informe um dia válido.'; return null; }
    const data = {
      titulo: title.value.trim(), descricao: desc.value.trim(), dia: day.value, prioridade: Number(prio.value),
      status: Number(status.value), categoria: cat, periodo: Number(period.value),
      tags: [...tagBoxes.querySelectorAll('input:checked')].map((c) => Number(c.value)),
      adiadaDe: task?.adiadaDe ?? null,
    };
    if (work) {
      const s0 = S.parseTime(start.value, -1);
      const e0 = S.parseTime(end.value, -1);
      if (!allDay.checked && !(s0 >= 0 && e0 > s0)) { err.textContent = 'O fim precisa ser depois do início.'; return null; }
      Object.assign(data, {
        tipo: Number(type.value), diaInteiro: allDay.checked,
        inicio: allDay.checked ? null : start.value, fim: allDay.checked ? null : end.value,
        periodo: S.periodFor(allDay.checked, allDay.checked ? -1 : s0),
      });
    }
    return data;
  };
  const save = (data) => write(setDoc(ref(S.COL.TASKS, task?.id ?? S.newId()), data, { merge: true }));

  openDialog(task ? 'Editar tarefa' : 'Nova tarefa',
    h('div', {}, field('Título', title), field('Descrição', desc),
      work ? field('Tipo de demanda', type) : null,
      h('div', { class: 'row' }, field('Dia', day), work ? null : field('Período', period)),
      work ? [h('label', { class: 'check' }, allDay, 'Dia inteiro'), times] : null,
      h('div', { class: 'row' }, field('Prioridade', prio), field('Status', status)),
      areaTags.length ? [h('label', {}, 'Tags'), tagBoxes] : null,
      // Adiar: o compromisso não ocorreu; pergunta a nova data e volta a ficar pendente
      work && task && !S.isClosed(task) ? h('div', { class: 'actions' },
        h('button', { type: 'button', class: 'secondary', onclick: () => {
          const data = collect();
          if (data) postponeTask(task, data, save);
        } }, 'Adiar')) : null,
      err),
    {
      onSave: () => {
        const data = collect();
        if (!data) return false;
        save(data);
        return true;
      },
      onDelete: task ? () => confirmDelete(task.titulo, 'A tarefa será apagada no celular e na web.',
        () => write(deleteDoc(ref(S.COL.TASKS, task.id)))) : null,
    });
}

/** Pergunta a nova data, guarda o dia original (na primeira vez) e volta a ficar pendente. */
function postponeTask(task, data, save) {
  const next = h('input', { type: 'date', value: S.addDays(task.dia > today() ? task.dia : today(), 1), required: true });
  openDialog('Adiada para quando?', field('Nova data', next), {
    saveLabel: 'Adiar',
    onSave: () => {
      if (!S.parseDay(next.value) || next.value === task.dia) return false;
      save({ ...data, dia: next.value, adiadaDe: task.adiadaDe ?? task.dia, status: 0 });
      toast(voc(`Adiada para ${S.formatDay(next.value)}, {voc}.`));
      return true;
    },
  });
}

/** Tags da área: Pessoal (paleta de 8 cores) ou Trabalho (grupo define a cor; o tom é automático). */
function editTag(tag, cat) {
  const work = cat === 'trabalho';
  const dark = isDark();
  if (!tag) {
    const list = [...state.tags.values()].filter((t) => (t.grupo >= 0) === work);
    openDialog(work ? 'Tags do Trabalho' : 'Tags', h('div', {},
      list.length ? list.map((t) => h('div', { class: 'item' },
        h('span', { class: 'stripe', style: { background: S.tagColor(t, dark) } }),
        h('div', { class: 'main' }, t.nome, work ? h('div', { class: 'caption' }, S.WORK_GROUPS[t.grupo][0]) : null),
        h('button', { type: 'button', class: 'text', onclick: () => editTag(t, cat) }, 'Editar')))
        : h('p', { class: 'empty' }, 'Nenhuma tag ainda.'),
      h('button', { type: 'button', class: 'secondary', onclick: () => editTag({}, cat) }, '+ Nova tag')));
    return;
  }
  const name = h('input', { maxlength: '24', required: true, value: tag.nome ?? '' });
  const color = work
    ? h('select', {}, S.WORK_GROUPS.map(([n], i) => h('option', { value: i, selected: i === (tag.grupo >= 0 ? tag.grupo : 0) }, n)))
    : h('select', {}, S.TAG_COLORS.map(([n], i) => h('option', { value: i, selected: i === (tag.cor ?? 0) }, n)));
  openDialog(tag.id ? 'Editar tag' : 'Nova tag', h('div', {}, field('Nome', name), field(work ? 'Grupo (define a cor)' : 'Cor', color)), {
    onSave: () => {
      if (!name.value.trim()) return false;
      let data;
      if (work) {
        const grupo = Number(color.value);
        // Tom novo só se a tag é nova ou mudou de grupo: o menos usado no grupo
        const cor = tag.id && tag.grupo === grupo ? tag.cor : S.nextShade(grupo, [...state.tags.values()], tag.id ?? 0);
        data = { nome: name.value.trim(), grupo, cor };
      } else {
        data = { nome: name.value.trim(), cor: Number(color.value), grupo: -1 };
      }
      write(setDoc(ref(S.COL.TAGS, tag.id ?? S.newId()), data));
      return true;
    },
    onDelete: tag.id ? () => {
      // A tag sai das tarefas que a usavam (o Firestore não faz isso sozinho)
      const batch = writeBatch(db);
      batch.delete(ref(S.COL.TAGS, tag.id));
      for (const t of state.tasks.values()) if (t.tags.includes(tag.id)) batch.set(ref(S.COL.TASKS, t.id), { tags: arrayRemove(tag.id) }, { merge: true });
      write(batch.commit());
    } : null,
  });
}

// ===================== Hábitos e água =====================

function waterInfo() {
  const goal = S.waterGoal(state.profile?.pesoKg, state.plan?.water?.mlPerKg);
  const ml = state.water.get(today())?.ml ?? 0;
  return { goal, ml };
}

function addWater(delta) {
  const { ml } = waterInfo();
  const next = Math.max(0, Math.min(S.MAX_DAY_ML, ml + delta));
  // Soma atômica: celular e web podem registrar ao mesmo tempo sem perder nada
  write(setDoc(ref(S.COL.WATER, today()), { ml: increment(next - ml) }, { merge: true }));
}

function waterCard(withButtons = false) {
  const { goal, ml } = waterInfo();
  const frac = goal > 0 ? Math.min(1, ml / goal) : 0;
  const fmt = (n) => n.toLocaleString('pt-BR');
  return h('div', { class: 'card' },
    h('div', { class: 'card-head' },
      h('div', { class: 'glass', 'aria-hidden': 'true' }, h('span', { style: { height: `${Math.round(frac * 100)}%` } })),
      h('div', { class: 'main' }, h('p', {}, 'Água de hoje'),
        h('p', { class: 'big', style: { color: 'var(--info)' } }, goal ? `${fmt(ml)} ml de ${fmt(goal)} ml` : `${fmt(ml)} ml`),
        h('p', { class: 'caption' }, !goal ? 'Informe seu peso no Perfil para calcular a meta.'
          : ml >= goal ? voc('Meta de água atingida, {voc}! Hidratação impecável.') : `Faltam ${fmt(goal - ml)} ml para a meta`))),
    withButtons ? [h('div', { class: 'water-buttons' }, [200, 300, 500].map((d) =>
      h('button', { type: 'button', onclick: () => addWater(d), 'aria-label': `Registrar ${d} ml de água` }, `+${d} ml`))),
    h('div', { class: 'actions' }, h('button', { class: 'text', onclick: adjustWater }, 'Ajustar'))] : null);
}

function adjustWater() {
  const input = h('input', { type: 'number', min: '0', max: String(S.MAX_DAY_ML), step: '50', value: waterInfo().ml });
  const err = h('p', { class: 'error' });
  openDialog('Água de hoje (ml)', h('div', {}, input, err), {
    onSave: () => {
      const v = Number(input.value);
      if (!(Number.isInteger(v) && v >= 0 && v <= S.MAX_DAY_ML)) { err.textContent = `Informe um valor entre 0 e ${S.MAX_DAY_ML} ml.`; return false; }
      write(setDoc(ref(S.COL.WATER, today()), { ml: v }));
      return true;
    },
  });
}

function renderHabits() {
  const t = today();
  const start = S.weekStart(t);
  const week = [...Array(7)].map((_, i) => S.addDays(start, i));
  const names = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'];
  const habits = [...state.habits.values()].sort((a, b) => a.posicao - b.posicao);
  return h('div', {}, waterCard(true),
    h('div', { class: 'actions' }, h('button', { class: 'primary', onclick: () => editHabit(null) }, '+ Novo hábito')),
    h('h2', {}, 'Meus hábitos'),
    habits.length === 0 ? h('p', { class: 'empty' }, 'Nenhum hábito ainda.') : null,
    habits.map((hb) => h('div', { class: 'card' },
      h('div', { class: 'card-head' }, h('h3', {}, hb.nome),
        h('span', { class: 'caption' }, `${week.filter((d) => hb.feitos.includes(d)).length}/7 nesta semana`),
        h('button', { class: 'icon', 'aria-label': `Editar ${hb.nome}`, onclick: () => editHabit(hb) }, '⋮')),
      h('div', { class: 'week' }, week.map((d, i) => {
        const on = hb.feitos.includes(d);
        return h('div', {}, h('span', { class: d === t ? 'today' : '' }, names[i]),
          h('button', { type: 'button', class: `dot${on ? ' on' : ''}${d === t ? ' today' : ''}`, disabled: d > t,
            'aria-pressed': String(on), 'aria-label': `${hb.nome}, ${names[i]}`,
            onclick: () => write(setDoc(ref(S.COL.HABITS, hb.id), { feitos: on ? arrayRemove(d) : arrayUnion(d) }, { merge: true })) }));
      })))));
}

function editHabit(hb) {
  const name = h('input', { maxlength: '80', required: true, value: hb?.nome ?? '' });
  openDialog(hb ? 'Editar hábito' : 'Novo hábito', field('Hábito (ex.: Beber 2 litros de água)', name), {
    onSave: () => {
      if (!name.value.trim()) return false;
      const pos = hb?.posicao ?? Math.max(-1, ...[...state.habits.values()].map((x) => x.posicao)) + 1;
      write(setDoc(ref(S.COL.HABITS, hb?.id ?? S.newId()), { nome: name.value.trim(), posicao: pos }, { merge: true }));
      return true;
    },
    onDelete: hb ? () => confirmDelete(hb.nome, 'O hábito e suas marcações serão apagados.',
      () => write(deleteDoc(ref(S.COL.HABITS, hb.id)))) : null,
  });
}

// ===================== Estudos =====================

/** Segundos do assunto no intervalo de dias [from, to], incluindo o cronômetro rodando. */
function subjectSeconds(s, from, to, now = Date.now()) {
  let total = 0;
  for (const [d, secs] of Object.entries(s.tempoPorDia)) if (d >= from && d <= to) total += secs;
  if (s.rodandoDesde > 0) for (const [d, secs] of Object.entries(S.splitByDay(s.rodandoDesde, now))) if (d >= from && d <= to) total += secs;
  return total;
}

function stopTimerInBatch(batch, s, now) {
  batch.set(ref(S.COL.SUBJECTS, s.id), S.stopTimerData(s.rodandoDesde, now, increment), { merge: true });
}

function toggleTimer(s) {
  const now = Date.now();
  const batch = writeBatch(db);
  if (s.rodandoDesde > 0) {
    stopTimerInBatch(batch, s, now);
  } else {
    // Só um cronômetro por vez (como no app): os outros pausam, guardando o tempo
    for (const o of state.subjects.values()) if (o.rodandoDesde > 0) stopTimerInBatch(batch, o, now);
    batch.set(ref(S.COL.SUBJECTS, s.id), { rodandoDesde: now }, { merge: true });
  }
  write(batch.commit());
}

function renderStudy() {
  const topics = [...state.topics.values()].sort((a, b) => a.posicao - b.posicao);
  if (!topics.some((t) => t.id === state.topicId)) state.topicId = topics[0]?.id ?? 0;
  const topic = state.topics.get(state.topicId);
  const t = today();
  const weekFrom = S.weekStart(t);
  const head = h('div', { class: 'chips' },
    topics.map((tp) => h('button', { type: 'button', class: 'chip', 'aria-pressed': String(tp.id === state.topicId),
      style: tp.id === state.topicId ? { background: S.STUDY_COLORS[tp.cor][1] } : {},
      onclick: () => { state.topicId = tp.id; render(); }, ondblclick: () => editTopic(tp) }, tp.nome)),
    h('button', { class: 'text', onclick: () => editTopic(null) }, '+ Matéria'),
    topic ? h('button', { class: 'text', onclick: () => editTopic(topic) }, 'Editar matéria') : null);
  if (!topic) return h('div', {}, head, h('p', { class: 'empty' }, voc('Crie a primeira matéria, {voc}: Inglês, Python, Violão…')));

  const color = S.STUDY_COLORS[topic.cor][1];
  const modules = [...state.modules.values()].filter((m) => m.materiaId === topic.id).sort((a, b) => a.posicao - b.posicao);
  const subjects = [...state.subjects.values()].filter((s) => s.materiaId === topic.id);
  // Assuntos sem módulo (o app aceita) aparecem num grupo próprio, em vez de sumirem
  if (subjects.some((s) => !s.moduloId || !state.modules.has(s.moduloId))) {
    modules.push({ id: null, materiaId: topic.id, nome: 'Sem módulo', posicao: Infinity, virtual: true });
  }
  const todayTotal = subjects.reduce((n, s) => n + subjectSeconds(s, t, t), 0);
  return h('div', {}, head,
    h('div', { class: 'card' }, h('p', {}, h('span', { class: 'big gold', 'data-topic-today': topic.id }, S.duration(todayTotal)),
      ' ', h('span', { class: 'caption' }, 'estudados hoje nesta matéria'))),
    h('div', { class: 'actions' }, h('button', { class: 'primary', onclick: () => editModule(null, topic) }, '+ Módulo')),
    modules.length === 0 ? h('p', { class: 'empty' }, voc('Organize a matéria em módulos, {voc}. Depois, crie os assuntos dentro de cada módulo.')) : null,
    modules.map((m) => {
      const list = subjects.filter((s) => (m.virtual ? !s.moduloId || !state.modules.has(s.moduloId) : s.moduloId === m.id))
        .sort((a, b) => a.posicao - b.posicao);
      const done = list.filter((s) => s.concluido).length;
      const pct = list.length ? Math.round((100 * done) / list.length) : 0;
      const total = list.reduce((n, s) => n + subjectSeconds(s, '0000-00-00', t), 0);
      return h('section', {},
        h('div', { class: 'card' },
          h('div', { class: 'card-head' }, h('h3', {}, m.nome), h('span', { class: 'gold' }, `${pct}%`),
            m.virtual ? null : h('button', { class: 'icon', 'aria-label': `Editar módulo ${m.nome}`, onclick: () => editModule(m, topic) }, '⋮')),
          h('div', { class: 'bar' }, h('span', { style: { width: `${pct}%` } })),
          h('p', { class: 'caption' }, `${done} de ${list.length} assunto${list.length === 1 ? '' : 's'} concluído${list.length === 1 ? '' : 's'} · Total ${S.duration(total)}`),
          m.virtual ? null : h('div', { class: 'actions' }, h('button', { class: 'text', onclick: () => editSubject(null, m) }, '+ Assunto'))),
        list.map((s) => {
          const running = s.rodandoDesde > 0;
          const mark = s.concluido ? 'var(--gold)' : color;
          return h('div', { class: 'card subject', style: running ? { borderColor: mark } : {} },
            h('div', { class: 'item' },
              h('span', { class: 'stripe', style: { background: mark } }),
              h('div', { class: 'main', role: 'button', tabindex: '0', onclick: () => editSubject(s, m) },
                h('div', { class: 'title', style: s.concluido ? { textDecoration: 'line-through' } : {} }, `${s.concluido ? '✓ ' : ''}${s.nome}`),
                h('div', {}, h('span', { class: 'timer', 'data-subject': s.id, style: running ? { color: mark } : {} },
                  S.clock(subjectSeconds(s, t, t))), ' ', h('span', { class: 'caption' }, 'hoje')),
                h('div', { class: 'caption' }, `Semana: ${S.duration(subjectSeconds(s, weekFrom, t))}`)),
              h('button', { class: 'play', 'aria-label': running ? `Pausar ${s.nome}` : `Iniciar ${s.nome}`, onclick: () => toggleTimer(s) },
                running ? '❚❚' : '▶')));
        }));
    }));
}

// Cronômetros: atualiza só os números a cada segundo (sem redesenhar a tela)
setInterval(() => {
  if (state.tab !== 'estudos' || !state.user) return;
  const t = today();
  for (const el of document.querySelectorAll('[data-subject]')) {
    const s = state.subjects.get(Number(el.dataset.subject));
    if (s?.rodandoDesde > 0) el.textContent = S.clock(subjectSeconds(s, t, t));
  }
  for (const el of document.querySelectorAll('[data-topic-today]')) {
    const id = Number(el.dataset.topicToday);
    const total = [...state.subjects.values()].filter((s) => s.materiaId === id).reduce((n, s) => n + subjectSeconds(s, t, t), 0);
    el.textContent = S.duration(total);
  }
}, 1000);

function editTopic(tp) {
  const name = h('input', { maxlength: '40', required: true, value: tp?.nome ?? '' });
  const color = h('select', {}, S.STUDY_COLORS.map(([n], i) => h('option', { value: i, selected: i === (tp?.cor ?? 0) }, n)));
  openDialog(tp ? 'Editar matéria' : 'O que deseja aprender?', h('div', {}, field('Ex.: Inglês, Python, Violão', name),
    field('Cor dos assuntos a estudar', color)), {
    onSave: () => {
      if (!name.value.trim()) return false;
      const id = tp?.id ?? S.newId();
      const pos = tp?.posicao ?? Math.max(-1, ...[...state.topics.values()].map((x) => x.posicao)) + 1;
      write(setDoc(ref(S.COL.TOPICS, id), { nome: name.value.trim(), cor: Number(color.value), posicao: pos }, { merge: true }));
      state.topicId = id;
      return true;
    },
    onDelete: tp ? () => confirmDelete(tp.nome, 'Todos os módulos, assuntos e o tempo registrado nesta matéria serão apagados.', () => {
      // Sem cascata no Firestore: apaga módulos e assuntos junto
      const batch = writeBatch(db);
      batch.delete(ref(S.COL.TOPICS, tp.id));
      for (const m of state.modules.values()) if (m.materiaId === tp.id) batch.delete(ref(S.COL.MODULES, m.id));
      for (const s of state.subjects.values()) if (s.materiaId === tp.id) batch.delete(ref(S.COL.SUBJECTS, s.id));
      write(batch.commit());
    }) : null,
  });
}

function editModule(m, topic) {
  const name = h('input', { maxlength: '60', required: true, value: m?.nome ?? '' });
  openDialog(m ? 'Renomear módulo' : 'Novo módulo', field('Módulo (ex.: Gramática)', name), {
    onSave: () => {
      if (!name.value.trim()) return false;
      const pos = m?.posicao ?? Math.max(-1, ...[...state.modules.values()].filter((x) => x.materiaId === topic.id).map((x) => x.posicao)) + 1;
      write(setDoc(ref(S.COL.MODULES, m?.id ?? S.newId()), { materiaId: topic.id, nome: name.value.trim(), posicao: pos }, { merge: true }));
      return true;
    },
    onDelete: m ? () => confirmDelete(m.nome, 'Todos os assuntos deste módulo e o tempo registrado neles serão apagados.', () => {
      const batch = writeBatch(db);
      batch.delete(ref(S.COL.MODULES, m.id));
      for (const s of state.subjects.values()) if (s.moduloId === m.id) batch.delete(ref(S.COL.SUBJECTS, s.id));
      write(batch.commit());
    }) : null,
  });
}

function editSubject(s, m) {
  const name = h('input', { maxlength: '60', required: true, value: s?.nome ?? '' });
  const done = h('input', { type: 'checkbox', checked: !!s?.concluido });
  const others = [...state.modules.values()].filter((x) => x.materiaId === m.materiaId);
  const move = s && others.length > (m.virtual ? 0 : 1)
    ? h('select', {}, m.virtual ? h('option', { value: '' }, 'Sem módulo') : null,
      others.map((x) => h('option', { value: x.id, selected: x.id === m.id }, x.nome))) : null;
  openDialog(s ? 'Editar assunto' : 'Novo assunto', h('div', {}, field('Assunto (ex.: Verbos irregulares)', name),
    s ? h('label', { class: 'check' }, done, 'Concluído') : null, move ? field('Módulo', move) : null), {
    onSave: () => {
      if (!name.value.trim()) return false;
      const moduloId = move && move.value ? Number(move.value) : m.id; // null = sem módulo
      const pos = s && moduloId === s.moduloId ? s.posicao
        : Math.max(-1, ...[...state.subjects.values()].filter((x) => x.moduloId === moduloId).map((x) => x.posicao)) + 1;
      const data = { materiaId: m.materiaId, moduloId, nome: name.value.trim(), posicao: pos, concluido: s ? done.checked : false };
      if (!s) data.rodandoDesde = 0;
      write(setDoc(ref(S.COL.SUBJECTS, s?.id ?? S.newId()), data, { merge: true }));
      return true;
    },
    onDelete: s ? () => confirmDelete(s.nome, 'O assunto e todo o tempo registrado nele serão apagados.',
      () => write(deleteDoc(ref(S.COL.SUBJECTS, s.id)))) : null,
  });
}

// ===================== Dieta =====================

/** Refeições abertas na tela (sobrevivem ao redesenho quando chega dado novo). */
const openMeals = new Set();

function renderDiet() {
  const t = today();
  const groups = S.groupMeals([...state.meals.values()].sort((a, b) => a.minutos - b.minutos || a.nome.localeCompare(b.nome)));
  const plan = state.plan;
  return h('div', {},
    plan ? h('p', { class: 'caption' }, `Planilha: ${plan.source || '—'}`) : null,
    groups.length === 0 ? h('div', { class: 'card' }, h('p', {}, 'Nenhuma refeição ainda.'),
      h('p', { class: 'caption' }, 'A planilha da nutricionista é importada pelo app Android (Configurações > Importar planilha da dieta).')) : null,
    groups.length ? h('h2', {}, `Refeições de hoje · ${groups.filter((g) => doneOption(g, t)).length}/${groups.length}`) : null,
    groups.map((g) => mealCard(g, t)),
    plan ? fullPlan(plan) : null);
}

const doneOption = (g, t) => g.options.find((o) => o.feitas.includes(t)) || null;

/** Onde fica o "o que comi": a opção feita, a que já tem texto ou a 1ª. */
const eatenTarget = (g, t) => doneOption(g, t) || g.options.find((o) => o.comido[t]) || g.options[0];

/** Marca a opção do dia e desmarca as outras do horário; o "o que comi" vem junto. */
function chooseOption(g, option, t) {
  const eaten = eatenTarget(g, t).comido[t] || '';
  const batch = writeBatch(db);
  for (const o of g.options) {
    if (o !== option) batch.set(ref(S.COL.MEALS, o.id), { feitas: arrayRemove(t), comido: { [t]: deleteField() } }, { merge: true });
  }
  batch.set(ref(S.COL.MEALS, option.id), { feitas: arrayUnion(t), comido: { [t]: eaten || deleteField() } }, { merge: true });
  write(batch.commit());
}

function clearDay(g, t) {
  const batch = writeBatch(db);
  for (const o of g.options) batch.set(ref(S.COL.MEALS, o.id), { feitas: arrayRemove(t), comido: { [t]: deleteField() } }, { merge: true });
  write(batch.commit());
}

/** Fechado: horário, nome e "feita hoje". Aberto: as opções (qual foi feita, alimentos), "O que comi". */
function mealCard(g, t) {
  const single = g.options.length === 1;
  const title = single ? g.options[0].nome : g.name;
  const done = doneOption(g, t);
  const target = eatenTarget(g, t);
  const eaten = target.comido[t] || '';
  const key = g.options[0].id;
  const summary = eaten ? `Comi: ${eaten}` : single ? '' : done ? `Feita: ${S.mealOptionLabel(done.nome)}` : `${g.options.length} opções`;
  const card = h('details', { class: `card meal${done ? ' done' : ''}`, open: openMeals.has(key) },
    h('summary', { class: 'card-head' },
      h('input', { type: 'checkbox', checked: !!done, style: { width: '20px', margin: 0 }, 'aria-label': `${title} feita hoje`,
        onclick: (e) => {
          e.stopPropagation(); // marcar não abre/fecha o cartão
          if (done) clearDay(g, t);
          else if (single) chooseOption(g, g.options[0], t);
          else { e.preventDefault(); card.open = true; toast('Escolha qual opção foi feita.'); }
        } }),
      h('div', { class: 'main' }, h('h3', {}, `${S.time(g.minutos)} · ${title}`),
        summary ? h('p', { class: 'caption' }, summary) : null)),
    g.options.map((o) => optionBlock(g, o, t, single, o === done)),
    eatenForm(g, target, t, eaten));
  card.addEventListener('toggle', () => { if (card.open) openMeals.add(key); else openMeals.delete(key); });
  return card;
}

function optionBlock(g, o, t, single, isDone) {
  const label = S.mealOptionLabel(o.nome);
  return h('div', { class: single ? '' : 'meal-option' },
    single ? null : h('label', { class: 'check' }, h('input', { type: 'radio', name: `meal-${g.options[0].id}`, checked: isDone,
      onclick: () => (isDone ? clearDay(g, t) : chooseOption(g, o, t)) }), ` ${label}`),
    o.itens.map((it) => h('div', {},
      h('div', { class: 'food' }, h('span', {}, it.alimento), h('span', { class: 'muted' }, it.quantidade)),
      it.substituicoes ? h('details', {}, h('summary', {}, 'Opções de substituição'),
        h('p', { class: 'caption', style: { whiteSpace: 'pre-line' } }, it.substituicoes)) : null)),
    o.observacoes ? h('p', { class: 'caption gold' }, o.observacoes) : null,
    h('div', { class: 'actions' }, h('button', { class: 'text', onclick: () => editMeal(o) }, single ? 'Editar refeição do plano' : `Editar ${label}`)));
}

/** "O que comi de verdade": salvar marca a opção como feita hoje (e desmarca as outras); vazio apaga o texto. */
function eatenForm(g, target, t, eaten) {
  const box = h('textarea', { maxlength: '500', placeholder: 'Ex.: troquei o arroz por batata-doce', 'aria-label': `O que comi em ${g.name}` }, eaten);
  return h('div', {},
    h('label', {}, 'O que comi de verdade'), box,
    h('div', { class: 'actions' }, h('button', { class: 'secondary', onclick: () => {
      const text = box.value.trim();
      const batch = writeBatch(db);
      for (const o of g.options) {
        if (o !== target) batch.set(ref(S.COL.MEALS, o.id), { feitas: arrayRemove(t), comido: { [t]: deleteField() } }, { merge: true });
      }
      batch.set(ref(S.COL.MEALS, target.id), { feitas: arrayUnion(t), comido: { [t]: text || deleteField() } }, { merge: true });
      write(batch.commit());
      toast(voc('Anotado, {voc}.'));
    } }, 'Salvar o que comi')));
}

/** Grupos de substituição, orientações e água: recolhidos, fora da lista de refeições. */
function fullPlan(plan) {
  return h('details', { class: 'card' }, h('summary', {}, 'Plano completo'),
    plan.groups?.length ? [h('h3', {}, 'Grupos de substituição'), plan.groups.map((g) => h('details', {},
      h('summary', {}, `Grupo ${g.number} · ${g.name}`),
      (g.items || []).map((f) => h('div', { class: 'food' }, h('span', {}, f.food), h('span', { class: 'muted' }, [f.qty, f.measure].filter(Boolean).join(' '))))))] : null,
    plan.notes?.length ? [h('h3', {}, 'Orientações'), plan.notes.map((n) =>
      h('div', { class: 'item' }, h('div', { class: 'main' }, h('div', { class: 'gold' }, n.topic), h('div', { class: 'caption', style: { whiteSpace: 'pre-line' } }, n.text))))] : null,
    plan.water?.tips ? [h('h3', {}, 'Água'), h('p', { class: 'caption', style: { whiteSpace: 'pre-line' } }, plan.water.tips)] : null);
}

function editMeal(m) {
  const name = h('input', { maxlength: '60', required: true, value: m.nome });
  const hour = h('input', { type: 'time', value: S.time(m.minutos), required: true });
  const notify = h('input', { type: 'checkbox', checked: m.avisar });
  const notes = h('textarea', { maxlength: '500' }, m.observacoes);
  openDialog('Editar refeição', h('div', {}, field('Nome', name), field('Horário', hour),
    h('label', { class: 'check' }, notify, 'Avisar no celular'), field('Observações', notes),
    h('p', { class: 'caption' }, 'Os alimentos são editados pelo app Android.')), {
    onSave: () => {
      if (!name.value.trim() || !hour.value) return false;
      write(setDoc(ref(S.COL.MEALS, m.id), { nome: name.value.trim(), horario: hour.value, avisar: notify.checked,
        observacoes: notes.value.trim() }, { merge: true }));
      return true;
    },
  });
}

// ===================== Perfil =====================

function renderProfile() {
  const p = state.profile;
  const name = h('input', { maxlength: '40', value: p.nome ?? '' });
  const form = h('select', {}, S.HONORIFICS.map((t, i) => h('option', { value: i, selected: i === p.tratamento }, t)));
  const weight = h('input', { type: 'number', min: '20', max: '400', step: '0.1', value: p.pesoKg ?? '' });
  const avatars = renderAvatarPicker(p.avatar ?? 0);
  const theme = h('select', {}, [['', 'Seguir o sistema'], ['dark', 'Escuro'], ['light', 'Claro']].map(([v, l]) =>
    h('option', { value: v, selected: (localStorage.getItem('alfred-theme') ?? '') === v }, l)));
  theme.onchange = () => setTheme(theme.value);
  const err = h('p', { class: 'error' });
  return h('div', {},
    h('div', { class: 'card' }, h('p', { class: 'big' }, p.nome || '—'), h('p', { class: 'caption' }, state.user.email || 'Conta Google')),
    h('div', { class: 'card' }, h('h3', {}, 'Seus dados'),
      field('Nome', name), field('Forma de tratamento', form), field('Peso (kg)', weight),
      h('label', {}, 'Ícone de perfil'), avatars, err,
      h('div', { class: 'actions' }, h('button', { class: 'primary', onclick: () => {
        const kg = parseFloat(String(weight.value).replace(',', '.'));
        if (!name.value.trim()) return void (err.textContent = 'Informe seu nome.');
        if (!(kg >= 20 && kg <= 400)) return void (err.textContent = 'Informe um peso entre 20 e 400 kg.');
        err.textContent = '';
        write(setDoc(userRef(), { nome: normalizeName(name.value), tratamento: Number(form.value), pesoKg: kg,
          avatar: Number(avatars.querySelector('input:checked').value) }, { merge: true }));
        toast(voc('Anotado, {voc}.'));
      } }, 'Salvar'))),
    h('div', { class: 'card' }, h('h3', {}, 'Aparência'), theme),
    h('div', { class: 'actions' }, h('button', { class: 'secondary danger', onclick: logout }, 'Sair da conta')));
}

function setTheme(value) {
  try {
    if (value) localStorage.setItem('alfred-theme', value);
    else localStorage.removeItem('alfred-theme');
  } catch { /* navegação privada: vale só nesta visita */ }
  if (value) document.documentElement.dataset.theme = value;
  else delete document.documentElement.dataset.theme;
}

try { setTheme(localStorage.getItem('alfred-theme') ?? ''); } catch { /* sem armazenamento */ }

setupLogin();
