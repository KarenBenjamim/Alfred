// Formato dos documentos no Firestore — o MESMO do app Android (CloudSchema.java).
// Qualquer mudança aqui precisa ser feita lá também.
//
//   usuarios/{uid}                         nome, tratamento, pesoKg, avatar, onboarding
//   usuarios/{uid}/tarefas/{id}            titulo, descricao, dia, prioridade, status, categoria, periodo, tags,
//                                          tipo, diaInteiro, inicio, fim, adiadaDe (Trabalho)
//   usuarios/{uid}/tags/{id}               nome, cor, grupo (-1 = Pessoal; 0..4 = grupo do Trabalho)
//   usuarios/{uid}/refeicoes/{id}          nome, horario, avisar, observacoes, itens, feitas, comido
//   usuarios/{uid}/plano/atual             plano importado da planilha (pelo app Android)
//   usuarios/{uid}/habitos/{id}            nome, posicao, feitos
//   usuarios/{uid}/agua/{dia}              ml
//   usuarios/{uid}/estudos_materias/{id}   nome, posicao, cor
//   usuarios/{uid}/estudos_modulos/{id}    materiaId, nome, posicao
//   usuarios/{uid}/estudos_assuntos/{id}   materiaId, moduloId, nome, posicao, concluido, rodandoDesde, tempoPorDia

export const COL = {
  USERS: 'usuarios', TASKS: 'tarefas', TAGS: 'tags', MEALS: 'refeicoes', PLAN: 'plano',
  HABITS: 'habitos', WATER: 'agua', TOPICS: 'estudos_materias', MODULES: 'estudos_modulos',
  SUBJECTS: 'estudos_assuntos',
};

export const PRIORITIES = ['Baixa', 'Média', 'Alta'];
export const STATUSES = ['Pendente', 'Em andamento', 'Concluída', 'Cancelada'];
export const STATUS_CANCELED = 3;
/** Tipo de demanda do Trabalho (mesma ordem de Task.TYPE_* no Android). */
export const DEMAND_TYPES = ['Reunião', 'Entrega', 'Desenvolvimento do projeto', 'Atividade externa', 'Treinamento', 'Outro'];
export const TYPE_OTHER = 5;
export const PERIODS = ['Manhã', 'Tarde', 'Noite'];
export const STATUS_DONE = 2;
export const HONORIFICS = ['Senhor', 'Senhora', 'Senhorita', 'Pelo nome'];
export const FORM_NAME = 3;
export const AVATARS = ['img/batcat_boy.png', 'img/batcat_girl.png', 'img/batdog_boy.png', 'img/batdog_girl.png'];

// Cores das tags (mesma ordem de TagColors no Android) e das matérias (StudyColors)
export const TAG_COLORS = [
  ['Lavanda', '#B39DDB'], ['Rosa', '#F48FB1'], ['Coral', '#FF8A80'], ['Laranja', '#FFB74D'],
  ['Menta', '#80CBC4'], ['Verde', '#A5D6A7'], ['Azul', '#90CAF9'], ['Cinza', '#B0BEC5'],
];
// Grupos das tags de Trabalho (Tag.GROUP_*): o grupo define a cor; cada tag ganha um dos 4 tons.
// [nome, tons no tema escuro, tons no tema claro] — os mesmos de values-night/ e values/ no Android.
export const WORK_GROUPS = [
  ['Projeto em que estou alocada', ['#7FB0F2', '#5B9BEA', '#A3C7F7', '#4F8FE8'], ['#1E5AA8', '#2F6FC9', '#174A8C', '#3B6FB8']],
  ['Bot em que estou dedicada', ['#7FD9A8', '#5CC98E', '#A3E6C2', '#3FBF7F'], ['#1F7A4D', '#2E8B57', '#176640', '#3A7F5C']],
  ['Atividade da empresa', ['#F7BC7F', '#F2A05C', '#FAD2A6', '#F29A3F'], ['#B35A12', '#A8520E', '#9A4C0E', '#B86A28']],
  ['Evento ou estudo', ['#BFA0EE', '#A685E6', '#D4BFF4', '#9B6FE0'], ['#6A3FB5', '#7A4FC2', '#5A3596', '#7F5AB8']],
  ['Outros', ['#F29FC8', '#EC82B6', '#F7C0DB', '#E86FA8'], ['#B0245A', '#C2306B', '#96204D', '#B84A78']],
];

export const STUDY_COLORS = [
  ['Azul-marinho', '#41608F'], ['Petróleo', '#2F6E73'], ['Verde-musgo', '#4E7A57'], ['Vinho', '#8A3B4F'],
  ['Terracota', '#A35F3E'], ['Ameixa', '#6C4C8A'], ['Rosa antigo', '#9A5A73'], ['Ardósia', '#5B6577'],
];

export const DEFAULT_ML_PER_KG = 35;
export const MAX_DAY_ML = 20000;

// ---------------- Ids e datas ----------------

const MAX_ID = 2 ** 53 - 1;

/** Id novo, aleatório e positivo, até 2^53 (o mesmo limite do Android). */
export function newId() {
  const r = new Uint32Array(2);
  let id = 0;
  while (id === 0) {
    crypto.getRandomValues(r);
    id = (r[0] & 0x1FFFFF) * 2 ** 32 + r[1];
  }
  return id;
}

export function parseId(docId) {
  if (!/^\d{1,16}$/.test(docId)) return 0;
  const n = Number(docId);
  return n > 0 && n <= MAX_ID ? n : 0;
}

const pad = (n) => String(n).padStart(2, '0');

/** Data local -> "2026-11-03". */
export function isoDay(date = new Date()) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** "2026-11-03" -> Date local à meia-noite (ou null se inválida). */
export function parseDay(s) {
  if (typeof s !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(s)) return null;
  const [y, m, d] = s.split('-').map(Number);
  const date = new Date(y, m - 1, d);
  return date.getMonth() === m - 1 && date.getDate() === d ? date : null;
}

export function addDays(iso, n) {
  const d = parseDay(iso);
  d.setDate(d.getDate() + n);
  return isoDay(d);
}

/** Segunda-feira da semana de "iso". */
export function weekStart(iso) {
  const d = parseDay(iso);
  const back = (d.getDay() + 6) % 7; // 0 = segunda
  d.setDate(d.getDate() - back);
  return isoDay(d);
}

export function formatDay(iso, today = isoDay()) {
  if (iso === today) return 'Hoje';
  if (iso === addDays(today, 1)) return 'Amanhã';
  if (iso === addDays(today, -1)) return 'Ontem';
  const d = parseDay(iso);
  return d ? d.toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit' }) : iso;
}

export function time(minutes) {
  const m = Math.max(0, Math.min(24 * 60 - 1, minutes));
  return `${pad(Math.floor(m / 60))}:${pad(m % 60)}`;
}

export function parseTime(s, fallback = 8 * 60) {
  if (typeof s !== 'string') return fallback;
  const [h, m] = s.split(':').map((x) => parseInt(x, 10));
  return h >= 0 && h < 24 && m >= 0 && m < 60 ? h * 60 + m : fallback;
}

/** 3725 s -> "1h 2min"; < 60 s -> "<1min". */
export function duration(seconds) {
  if (seconds < 60) return seconds > 0 ? '<1min' : '0min';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  return h > 0 ? `${h}h ${m}min` : `${m}min`;
}

export function clock(seconds) {
  const s = Math.max(0, Math.floor(seconds));
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;
}

/**
 * Divide o intervalo [start, end) (ms) entre os dias locais, como StudyTime.splitByDay:
 * das 23h à 1h = 1h ontem + 1h hoje. Devolve {"2026-11-03": segundos}.
 */
export function splitByDay(startMs, endMs) {
  const out = {};
  if (!(endMs > startMs)) return out;
  let cur = startMs;
  while (cur < endMs) {
    const d = new Date(cur);
    const next = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1).getTime();
    const stop = Math.min(next, endMs);
    const secs = Math.floor((stop - cur) / 1000);
    if (secs > 0) out[isoDay(d)] = (out[isoDay(d)] || 0) + secs;
    cur = stop;
  }
  return out;
}

// ---------------- Leitura tolerante (igual ao Android) ----------------

const str = (v, f = '') => (typeof v === 'string' ? v : f);
const num = (v, f = 0) => (typeof v === 'number' && Number.isFinite(v) ? v : f);
const bool = (v, f = false) => (typeof v === 'boolean' ? v : f);
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, Math.trunc(v)));

export function days(raw) {
  return Array.isArray(raw) ? [...new Set(raw.filter((d) => parseDay(d)))] : [];
}

export function readTask(id, d) {
  if (!id || !parseDay(d.dia)) return null;
  return {
    id, titulo: str(d.titulo), descricao: str(d.descricao), dia: d.dia,
    prioridade: clamp(num(d.prioridade, 1), 0, 2), status: clamp(num(d.status, 0), 0, 3),
    categoria: d.categoria === 'pessoal' ? 'pessoal' : 'trabalho', periodo: clamp(num(d.periodo, 0), 0, 2),
    tags: Array.isArray(d.tags) ? [...new Set(d.tags.filter((t) => typeof t === 'number' && t > 0))] : [],
    tipo: clamp(num(d.tipo, TYPE_OTHER), 0, DEMAND_TYPES.length - 1),
    diaInteiro: bool(d.diaInteiro),
    inicio: parseTime(d.inicio, -1), fim: parseTime(d.fim, -1),
    adiadaDe: parseDay(d.adiadaDe) ? d.adiadaDe : null,
  };
}

/** Encerrada = concluída ou cancelada (sem atraso, fora das pendências). */
export const isClosed = (t) => t.status >= 2;
export const hasTime = (t) => !t.diaInteiro && t.inicio >= 0;

/** Período que corresponde ao horário (como Task.periodFor): agrupa como as tarefas antigas. */
export function periodFor(diaInteiro, inicio) {
  if (diaInteiro || inicio < 0) return 0;
  return inicio < 13 * 60 ? 0 : 1;
}

export function readTag(id, d) {
  if (!id) return null;
  const g = num(d.grupo, -1);
  const grupo = Number.isInteger(g) && g >= 0 && g < WORK_GROUPS.length ? g : -1;
  return { id, nome: str(d.nome), grupo, cor: grupo >= 0 ? clamp(num(d.cor, 0), 0, 3) : clamp(num(d.cor, 7), 0, 7) };
}

/** Tom para uma tag nova no grupo: o menos usado (como WorkTagColors.nextShade). */
export function nextShade(grupo, tags, ignoreId = 0) {
  const used = [0, 0, 0, 0];
  for (const t of tags) if (t.id !== ignoreId && t.grupo === grupo) used[t.cor]++;
  let best = 0;
  for (let s = 1; s < 4; s++) if (used[s] < used[best]) best = s;
  return best;
}

/** Cor da tag no tema atual (Pessoal: paleta de 8; Trabalho: grupo e tom). */
export function tagColor(tag, dark = true) {
  if (tag.grupo >= 0) return WORK_GROUPS[tag.grupo][dark ? 1 : 2][tag.cor];
  return TAG_COLORS[tag.cor][1];
}

export function readMeal(id, d) {
  if (!id) return null;
  const itens = Array.isArray(d.itens) ? d.itens.filter((i) => i && typeof i === 'object').map((i) => ({
    alimento: str(i.alimento), quantidade: str(i.quantidade), substituicoes: str(i.substituicoes),
    grupo: num(i.grupo), porcoes: str(i.porcoes),
  })) : [];
  return {
    id, nome: str(d.nome), minutos: parseTime(d.horario), avisar: bool(d.avisar, true),
    observacoes: str(d.observacoes), itens,
    // "comido" {dia: texto}; um dia com texto também conta como feito (igual ao Android)
    comido: readEaten(d.comido),
    feitas: [...new Set([...days(d.feitas), ...Object.keys(readEaten(d.comido))])],
  };
}

/** "Opção 2", "opcao 2", "Op. 2" no fim do nome (mesma regra do MealGroups do Android). */
const OPTION_SUFFIX = /\s*[-–—:(]?\s*(?<!\p{L})(op(?:ç|c)?(?:ão|ao)?\.?)\s*(\d+)\s*\)?\s*$/iu;

function optionMatch(name) {
  const m = OPTION_SUFFIX.exec(name);
  return m && m.index > 0 ? m : null;
}

/** "Lanche - Opção 2" -> "Lanche". */
export function mealBaseName(name) {
  const m = optionMatch(name);
  return m ? name.slice(0, m.index).trim() : name;
}

/** "Lanche - Opção 2" -> "Opção 2" (ou o nome inteiro). */
export function mealOptionLabel(name) {
  const m = optionMatch(name);
  return m ? `Opção ${m[2]}` : name;
}

/**
 * Junta as opções de uma refeição ("Lanche - Opção 1/2") num grupo só, mesmo em horários
 * diferentes; refeições sem "Opção N" ficam sozinhas. O grupo fica no horário da 1ª opção.
 */
export function groupMeals(meals) {
  const map = new Map();
  for (const m of meals) {
    const name = mealBaseName(m.nome);
    const key = optionMatch(m.nome) ? `o|${name.toLowerCase()}` : `m|${m.id}`;
    if (!map.has(key)) map.set(key, { name, minutos: m.minutos, options: [] });
    map.get(key).options.push(m);
  }
  return [...map.values()];
}

function readEaten(raw) {
  const out = {};
  if (raw && typeof raw === 'object') for (const [day, text] of Object.entries(raw)) if (parseDay(day) && typeof text === 'string' && text.trim()) out[day] = text;
  return out;
}

export function readHabit(id, d) {
  return id ? { id, nome: str(d.nome), posicao: num(d.posicao), feitos: days(d.feitos) } : null;
}

export function readTopic(id, d) {
  return id ? { id, nome: str(d.nome), posicao: num(d.posicao), cor: clamp(num(d.cor), 0, 7) } : null;
}

export function readModule(id, d) {
  const materiaId = num(d.materiaId);
  return id && materiaId > 0 ? { id, materiaId, nome: str(d.nome), posicao: num(d.posicao) } : null;
}

export function readSubject(id, d) {
  const materiaId = num(d.materiaId);
  if (!id || materiaId <= 0) return null;
  const tempo = {};
  if (d.tempoPorDia && typeof d.tempoPorDia === 'object') {
    for (const [day, s] of Object.entries(d.tempoPorDia)) if (parseDay(day) && num(s) > 0) tempo[day] = num(s);
  }
  const moduloId = num(d.moduloId);
  return {
    id, materiaId, moduloId: moduloId > 0 ? moduloId : null, nome: str(d.nome), posicao: num(d.posicao),
    concluido: bool(d.concluido), rodandoDesde: Math.max(0, num(d.rodandoDesde)), tempoPorDia: tempo,
  };
}

// ---------------- Tratamento (Honorific no Android) ----------------

const TITLES = ['senhor', 'senhora', 'senhorita'];
const cap = (s) => (s ? s[0].toUpperCase() + s.slice(1) : s);

export function vocative(form, name) {
  return form >= 0 && form < 3 ? TITLES[form] : (name || '').trim();
}

export function fullName(form, name) {
  const n = (name || '').trim();
  if (!(form >= 0 && form < 3)) return n;
  return n ? `${cap(TITLES[form])} ${n}` : cap(TITLES[form]);
}

/** Troca {voc}/{Voc} pelo tratamento; sem tratamento, o vocativo some com a vírgula. */
export function honor(text, form, name) {
  const voc = vocative(form, name);
  if (!voc) return text.replace(', {voc}', '').replace('{Voc}, ', '').replace('{voc}', '').replace('{Voc}', '');
  return text.replace('{Voc}', cap(voc)).replace('{voc}', voc);
}

/**
 * Meta de água: peso × ml por kg (o do plano, se houver; senão 35).
 * A conta é feita em float de 32 bits, como no Android (WaterGoal.goalMl): 70,1 kg × 35 dá
 * 2454 ml nos dois (em double daria 2453,4999… e arredondaria para 2453).
 */
export function waterGoal(pesoKg, mlPerKg) {
  if (!(pesoKg >= 20 && pesoKg <= 400)) return 0;
  return Math.round(Math.fround(Math.fround(pesoKg) * (mlPerKg > 0 ? mlPerKg : DEFAULT_ML_PER_KG)));
}

/**
 * O que gravar no assunto ao pausar o cronômetro (iniciado em startMs): o tempo de cada dia
 * como increment e rodandoDesde = 0. Sem segundos a somar (menos de 1 s, ou relógio de outro
 * aparelho adiantado) NÃO manda tempoPorDia: um mapa vazio com merge substituiria o tempo de
 * todos os dias do assunto.
 *
 * @param inc a função increment do Firestore
 */
export function stopTimerData(startMs, nowMs, inc) {
  const time = {};
  for (const [d, secs] of Object.entries(splitByDay(startMs, nowMs))) time[d] = inc(secs);
  return Object.keys(time).length ? { tempoPorDia: time, rodandoDesde: 0 } : { rodandoDesde: 0 };
}
