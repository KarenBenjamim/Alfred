// Testes de web/js/schema.js (o espelho do CloudSchema.java), sem navegador nem emulador.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as S from '../web/js/schema.js';

test('newId: sempre inteiro seguro, positivo e dentro de 2^53', () => {
  for (let i = 0; i < 20000; i++) {
    const id = S.newId();
    assert.ok(Number.isSafeInteger(id) && id > 0 && id <= 2 ** 53 - 1, String(id));
    assert.equal(S.parseId(String(id)), id);
  }
});

test('parseId: iguais ao CloudSchema.parseId (limites, lixo, zeros)', () => {
  assert.equal(S.parseId('1'), 1);
  assert.equal(S.parseId('9007199254740991'), 9007199254740991);
  assert.equal(S.parseId('9007199254740992'), 0);   // 2^53 já não é seguro
  assert.equal(S.parseId('0'), 0);
  assert.equal(S.parseId('-5'), 0);
  assert.equal(S.parseId('abc'), 0);
  assert.equal(S.parseId('12345678901234567'), 0); // 17 dígitos
  assert.equal(S.parseId('atual'), 0);
});

test('parseDay: só datas ISO reais', () => {
  assert.ok(S.parseDay('2026-11-03'));
  assert.equal(S.parseDay('2026-02-30'), null);
  assert.equal(S.parseDay('2026-1-3'), null);
  assert.equal(S.parseDay(null), null);
  assert.equal(S.parseDay(20261103), null);
  assert.equal(S.addDays('2026-12-31', 1), '2027-01-01');
  assert.equal(S.weekStart('2026-11-08'), '2026-11-02'); // domingo -> segunda anterior
});

test('parseTime/time: fronteiras e lixo caem no padrão (como o Android)', () => {
  assert.equal(S.parseTime('08:30'), 510);
  assert.equal(S.parseTime('24:00'), 480);
  assert.equal(S.parseTime('12:60'), 480);
  assert.equal(S.parseTime('8'), 480);
  assert.equal(S.parseTime(null), 480);
  assert.equal(S.time(-5), '00:00');
  assert.equal(S.time(24 * 60 + 5), '23:59');
});

test('readTask: documento do Android (Long/Double, tags repetidas) é lido com segurança', () => {
  const t = S.readTask(5, { titulo: 'x', dia: '2026-11-03', prioridade: 9, status: -1, categoria: 'qualquer',
    periodo: 7, tags: [1, 1, 2, 'x', -3, 0] });
  assert.deepEqual([t.prioridade, t.status, t.categoria, t.periodo, t.tags], [2, 0, 'trabalho', 2, [1, 2]]);
  assert.equal(S.readTask(5, { titulo: 'sem dia' }), null);
  assert.equal(S.readTask(0, { dia: '2026-11-03' }), null);
});

test('readMeal: itens do Android e "feitas" com lixo', () => {
  const m = S.readMeal(1, { nome: 'A', horario: '12:00', itens: [{ alimento: 'Arroz', grupo: 1 }, null, 3],
    feitas: ['2026-11-03', '2026-11-03', 'ontem'] });
  assert.equal(m.minutos, 720);
  assert.equal(m.avisar, true);
  assert.equal(m.itens.length, 1);
  assert.deepEqual(m.feitas, ['2026-11-03']);
});

test('readSubject: moduloId nulo/0, tempo inválido e rodandoDesde negativo', () => {
  const s = S.readSubject(9, { materiaId: 1, moduloId: null, rodandoDesde: -5,
    tempoPorDia: { '2026-11-03': 90, '2026-11-04': 0, lixo: 5, '2026-11-05': 'x' } });
  assert.equal(s.moduloId, null);
  assert.equal(s.rodandoDesde, 0);
  assert.deepEqual(s.tempoPorDia, { '2026-11-03': 90 });
  assert.equal(S.readSubject(9, { nome: 'sem matéria' }), null);
  assert.equal(S.readModule(9, { materiaId: 0 }), null);
});

test('splitByDay: meia-noite divide o tempo e intervalo vazio/invertido dá {}', () => {
  const a = new Date(2026, 10, 3, 23, 0, 0).getTime();
  const b = new Date(2026, 10, 4, 1, 0, 0).getTime();
  assert.deepEqual(S.splitByDay(a, b), { '2026-11-03': 3600, '2026-11-04': 3600 });
  assert.deepEqual(S.splitByDay(b, a), {});
  assert.deepEqual(S.splitByDay(a, a + 500), {});
});

test('waterGoal: faixa do peso e ml/kg do plano', () => {
  assert.equal(S.waterGoal(70, 0), 2450);
  assert.equal(S.waterGoal(70, 40), 2800);
  assert.equal(S.waterGoal(19.9, 35), 0);
  assert.equal(S.waterGoal(undefined, 35), 0);
});

test('tratamento: mesmas frases do Honorific', () => {
  assert.equal(S.fullName(0, 'Ana'), 'Senhor Ana');
  assert.equal(S.fullName(3, ' Ana '), 'Ana');
  assert.equal(S.honor('Olá, {voc}.', 3, ''), 'Olá.');
  assert.equal(S.honor('{Voc}, vamos', 1, 'Ana'), 'Senhora, vamos');
});

test('meta de água igual à do Android (conta em float), com o peso exato que o Android grava', () => {
  // O Android grava 70.1 exato (UserProfile.weightKgExact) e calcula em float: 70.1f * 35 = 2453.5f -> 2454
  assert.equal(S.waterGoal(70.1, 35), 2454);
  // E com o valor antigo, com ruído de float, o resultado é o mesmo
  assert.equal(S.waterGoal(Math.fround(70.1), 35), 2454);
  assert.equal(S.waterGoal(65, 35), 2275);
});

test('pausar o cronômetro: soma o tempo por dia e, sem segundos, não manda tempoPorDia', () => {
  const inc = (n) => ({ inc: n });
  const start = new Date(2026, 10, 1, 23, 30).getTime();
  const data = S.stopTimerData(start, start + 3600 * 1000, inc); // 23:30 -> 00:30
  assert.deepEqual(data, { tempoPorDia: { '2026-11-01': { inc: 1800 }, '2026-11-02': { inc: 1800 } }, rodandoDesde: 0 });
  assert.deepEqual(S.stopTimerData(start, start + 500, inc), { rodandoDesde: 0 });   // menos de 1 s
  assert.deepEqual(S.stopTimerData(start, start - 60000, inc), { rodandoDesde: 0 }); // relógio adiantado
});

// ---------- Trabalho: tipo, horário, adiar/cancelar e tags por grupo ----------

test('readTask: campos do Trabalho e documento antigo sem eles', () => {
  const t = S.readTask(9, { dia: '2026-11-04', status: 3, tipo: 0, diaInteiro: false, inicio: '14:00', fim: '15:30', adiadaDe: '2026-11-02' });
  assert.equal(t.status, S.STATUS_CANCELED);
  assert.equal(t.tipo, 0);
  assert.equal(t.inicio, 14 * 60);
  assert.equal(t.fim, 15 * 60 + 30);
  assert.equal(t.adiadaDe, '2026-11-02');
  assert.ok(S.isClosed(t));
  const old = S.readTask(1, { dia: '2026-11-04', periodo: 1 });
  assert.equal(old.tipo, S.TYPE_OTHER);
  assert.equal(old.inicio, -1);
  assert.equal(old.adiadaDe, null);
  assert.equal(S.hasTime(old), false);
  assert.equal(S.readTask(2, { dia: '2026-11-04', status: 9 }).status, 3); // fora da faixa: limitado
});

test('periodFor: mesmo agrupamento do Android', () => {
  assert.equal(S.periodFor(false, 9 * 60), 0);
  assert.equal(S.periodFor(false, 14 * 60), 1);
  assert.equal(S.periodFor(true, -1), 0);
});

test('tags: grupo do Trabalho, tom automático e cor por tema', () => {
  const projeto = S.readTag(1, { nome: 'Atlas', grupo: 0, cor: 0 });
  const pessoal = S.readTag(2, { nome: 'Casa', cor: 3 });
  assert.equal(projeto.grupo, 0);
  assert.equal(pessoal.grupo, -1);
  assert.equal(S.readTag(3, { nome: 'x', grupo: 99 }).grupo, -1); // grupo inválido: vira tag do Pessoal
  assert.equal(S.nextShade(0, [projeto, S.readTag(4, { grupo: 0, cor: 1 })]), 2);
  assert.equal(S.tagColor(projeto, true), '#7FB0F2');  // azul aberto no escuro
  assert.equal(S.tagColor(projeto, false), '#1E5AA8'); // azul fechado no claro
});

test('readMeal: "comido" por dia e dia com texto conta como feito', () => {
  const m = S.readMeal(5, { nome: 'Almoço', horario: '12:00', feitas: ['2026-11-03'],
    comido: { '2026-11-04': 'Batata-doce no lugar do arroz', '2026-11-02': '  ', lixo: 'x' } });
  assert.deepEqual(m.comido, { '2026-11-04': 'Batata-doce no lugar do arroz' });
  assert.ok(m.feitas.includes('2026-11-04'));
  assert.ok(m.feitas.includes('2026-11-03'));
  assert.deepEqual(S.readMeal(6, { nome: 'Lanche' }).comido, {});
});

test('opções do mesmo horário viram uma refeição só (igual ao MealGroups do Android)', () => {
  const m = (id, nome, minutos) => ({ id, nome, minutos });
  const g = S.groupMeals([m(1, 'Lanche - Opção 1', 960), m(2, 'Lanche - Opção 2', 960), m(3, 'Jantar - Opção 1', 1200), m(4, 'Almoço', 720)]);
  assert.deepEqual(g.map((x) => [x.name, x.options.length]), [['Lanche', 2], ['Jantar', 1], ['Almoço', 1]]);
  assert.equal(S.mealOptionLabel('Lanche - Opção 2'), 'Opção 2');
  assert.equal(S.mealBaseName('Almoço'), 'Almoço');
  assert.equal(S.mealBaseName('Café da manhã (opção 3)'), 'Café da manhã');
  assert.equal(S.mealOptionLabel('Jantar opcao 1'), 'Opção 1');
  assert.equal(S.mealBaseName('Workshop 2'), 'Workshop 2');
  const all = S.groupMeals([m(1, 'Café - Opção 1', 420), m(2, 'Café - Opção 2', 450), m(3, 'Jantar', 1200), m(4, 'Jantar', 1260), m(5, 'lanche – opcao 2', 960), m(6, 'Lanche - Op. 1', 960)]);
  assert.deepEqual(all.map((x) => x.options.length), [2, 1, 1, 2]);
});
