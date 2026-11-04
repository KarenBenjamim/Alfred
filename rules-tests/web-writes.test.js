// Gravações que o app.js (web) e o CloudSync (Android) fazem, contra as regras reais e o SDK web,
// no emulador local:
//   firebase emulators:exec --only firestore --project demo-alfred "npm --prefix rules-tests test"
import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, writeBatch, arrayUnion, arrayRemove, increment } from 'firebase/firestore';
import * as S from '../web/js/schema.js';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-alfred',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8080 },
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const ana = () => env.authenticatedContext('ana').firestore();
const sub = (db, id) => doc(db, `usuarios/ana/estudos_assuntos/${id}`);

// ---------- Documentos reais das duas plataformas passam pelas regras ----------

test('perfil gravado pelo Android e pela web (todos os campos) é aceito', async () => {
  const db = ana();
  await assertSucceeds(setDoc(doc(db, 'usuarios/ana'),
    { nome: 'Ana', tratamento: 1, pesoKg: 70.0999984741211, avatar: 2, onboarding: true }, { merge: true }));
});

test('tarefa, refeição com itens, hábito e assunto com tempoPorDia são aceitos', async () => {
  const db = ana();
  await assertSucceeds(setDoc(doc(db, 'usuarios/ana/tarefas/9007199254740991'), {
    titulo: 't', descricao: '', dia: '2026-11-03', prioridade: 1, status: 0, categoria: 'pessoal', periodo: 2, tags: [1, 2],
  }));
  await assertSucceeds(setDoc(doc(db, 'usuarios/ana/refeicoes/5'), {
    nome: 'Almoço', horario: '12:00', avisar: true, observacoes: '',
    itens: [{ alimento: 'Arroz', quantidade: '2 col.', substituicoes: 'a\nb', grupo: 1, porcoes: '1' }],
  }, { merge: true }));
  await assertSucceeds(setDoc(doc(db, 'usuarios/ana/habitos/6'), { nome: 'h', posicao: 0, feitos: [] }));
  await assertSucceeds(setDoc(sub(db, 7), {
    materiaId: 1, moduloId: null, nome: 's', posicao: 0, concluido: false, rodandoDesde: 0,
    tempoPorDia: { '2026-11-03': 86 },
  }));
});

test('plano importado (arrays de mapas aninhados) é aceito', async () => {
  await assertSucceeds(setDoc(doc(ana(), 'usuarios/ana/plano/atual'), {
    source: 'x.xlsx', importedAt: 1, meals: [{ time: '08:00', name: 'Café', notes: '', items: [
      { food: 'Pão', measure: 'fatia', qty: '2', substitutions: ['a', 'b'], group: 1, portions: '1' }] }],
    groups: [{ number: 1, name: 'g', items: [{ food: 'f', measure: 'm', qty: '1' }] }],
    notes: [{ topic: 't', text: 'x' }], water: { mlPerKg: 35, tips: '' },
  }));
});

test('limite de 60 campos: 60 passa, 61 não, e um merge que passa de 60 também é recusado', async () => {
  const db = ana();
  const mk = (n) => Object.fromEntries([...Array(n).keys()].map((i) => ['c' + i, i]));
  await assertSucceeds(setDoc(doc(db, 'usuarios/ana/tarefas/1'), mk(60)));
  await assertFails(setDoc(doc(db, 'usuarios/ana/tarefas/2'), mk(61)));
  await assertFails(setDoc(doc(db, 'usuarios/ana/tarefas/1'), { extra: 1 }, { merge: true }));
});

// ---------- Soma atômica e uniões (celular + web ao mesmo tempo) ----------

test('água: increment em dia sem documento cria, soma e aceita delta negativo', async () => {
  const ref = doc(ana(), 'usuarios/ana/agua/2026-11-03');
  await assertSucceeds(setDoc(ref, { ml: increment(200) }, { merge: true }));
  await assertSucceeds(setDoc(ref, { ml: increment(300) }, { merge: true }));
  await assertSucceeds(setDoc(ref, { ml: increment(-100) }, { merge: true }));
  assert.equal((await getDoc(ref)).data().ml, 400);
});

test('refeição: arrayUnion não duplica e arrayRemove desfaz; o merge do cadastro preserva "feitas"', async () => {
  const ref = doc(ana(), 'usuarios/ana/refeicoes/5');
  await setDoc(ref, { nome: 'A', feitas: arrayUnion('2026-11-03') }, { merge: true });
  await setDoc(ref, { feitas: arrayUnion('2026-11-03') }, { merge: true });
  await setDoc(ref, { nome: 'B', horario: '09:00' }, { merge: true }); // pushMeal do Android
  assert.deepEqual((await getDoc(ref)).data().feitas, ['2026-11-03']);
  await setDoc(ref, { feitas: arrayRemove('2026-11-03') }, { merge: true });
  assert.deepEqual((await getDoc(ref)).data().feitas, []);
});

test('tempo de estudo: increments por dia somam e o merge do assunto não apaga tempoPorDia', async () => {
  const db = ana();
  await setDoc(sub(db, 7), { nome: 's', rodandoDesde: 1, tempoPorDia: { '2026-11-03': increment(60) } }, { merge: true });
  await setDoc(sub(db, 7), { tempoPorDia: { '2026-11-03': increment(30), '2026-11-04': increment(5) }, rodandoDesde: 0 }, { merge: true });
  await setDoc(sub(db, 7), { nome: 'renomeado', rodandoDesde: 0 }, { merge: true }); // pushSubject do Android
  const d = (await getDoc(sub(db, 7))).data();
  assert.deepEqual(d.tempoPorDia, { '2026-11-03': 90, '2026-11-04': 5 });
});

// ---------- BUG: web/js/app.js stopTimerInBatch ----------

test('pausar o cronômetro sem segundos a somar (<1 s ou relógio atrasado) NÃO apaga tempoPorDia', async () => {
  const db = ana();
  await setDoc(sub(db, 8), { nome: 's', rodandoDesde: 0, tempoPorDia: { '2026-11-01': 3600 } });
  // O que a web grava de fato (S.stopTimerData), com o relógio de outro aparelho adiantado
  const now = Date.now();
  const batch = writeBatch(db);
  batch.set(sub(db, 8), S.stopTimerData(now + 60000, now, increment), { merge: true });
  await batch.commit();
  assert.deepEqual((await getDoc(sub(db, 8))).data().tempoPorDia, { '2026-11-01': 3600 });
});

test('armadilha do Firestore documentada: mapa vazio com merge substitui o campo inteiro', async () => {
  const db = ana();
  await setDoc(sub(db, 9), { nome: 's', tempoPorDia: { '2026-11-01': 3600 } });
  await setDoc(sub(db, 9), { tempoPorDia: {} }, { merge: true });
  assert.deepEqual((await getDoc(sub(db, 9))).data().tempoPorDia, {}); // por isso stopTimerData não manda {}
});
