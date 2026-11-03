// Testes das regras do Firestore (firestore.rules), rodando no emulador local:
//   firebase emulators:exec --only firestore "npm --prefix rules-tests test"
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc, collection, getDocs } from 'firebase/firestore';

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
const bruno = () => env.authenticatedContext('bruno').firestore();
const anonimo = () => env.unauthenticatedContext().firestore();

test('dono lê e grava o próprio perfil', async () => {
  await assertSucceeds(setDoc(doc(ana(), 'usuarios/ana'), { nome: 'Ana', pesoKg: 60 }));
  await assertSucceeds(getDoc(doc(ana(), 'usuarios/ana')));
});

test('outro usuário NÃO lê nem grava o perfil alheio', async () => {
  await assertSucceeds(setDoc(doc(ana(), 'usuarios/ana'), { nome: 'Ana' }));
  await assertFails(getDoc(doc(bruno(), 'usuarios/ana')));
  await assertFails(setDoc(doc(bruno(), 'usuarios/ana'), { nome: 'Invasor' }));
});

test('sem login, nada é acessível', async () => {
  await assertFails(getDoc(doc(anonimo(), 'usuarios/ana')));
  await assertFails(setDoc(doc(anonimo(), 'usuarios/ana/tarefas/1'), { titulo: 'x' }));
});

test('dono usa todas as coleções da estrutura aprovada', async () => {
  for (const col of ['tarefas', 'tags', 'refeicoes', 'plano', 'habitos', 'agua',
                     'estudos_materias', 'estudos_modulos', 'estudos_assuntos']) {
    await assertSucceeds(setDoc(doc(ana(), `usuarios/ana/${col}/1`), { nome: 'ok' }));
    await assertSucceeds(getDoc(doc(ana(), `usuarios/ana/${col}/1`)));
    await assertSucceeds(deleteDoc(doc(ana(), `usuarios/ana/${col}/1`)));
  }
});

test('outro usuário NÃO lista nem apaga as tarefas alheias', async () => {
  await assertSucceeds(setDoc(doc(ana(), 'usuarios/ana/tarefas/1'), { titulo: 'Segredo' }));
  await assertFails(getDocs(collection(bruno(), 'usuarios/ana/tarefas')));
  await assertFails(deleteDoc(doc(bruno(), 'usuarios/ana/tarefas/1')));
});

test('coleção fora da lista é negada, mesmo para o dono', async () => {
  await assertFails(setDoc(doc(ana(), 'usuarios/ana/qualquer/1'), { x: 1 }));
});

test('nada fora de usuarios/{uid} é acessível', async () => {
  await assertFails(setDoc(doc(ana(), 'publico/1'), { x: 1 }));
  await assertFails(getDoc(doc(ana(), 'publico/1')));
});

test('documento com campos demais é recusado', async () => {
  const grande = {};
  for (let i = 0; i < 61; i++) grande['c' + i] = i;
  await assertFails(setDoc(doc(ana(), 'usuarios/ana/tarefas/1'), grande));
});
