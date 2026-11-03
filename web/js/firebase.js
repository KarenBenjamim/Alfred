// Inicialização do Firebase na web (SDK modular pelo CDN oficial do Google).
//  - No Firebase Hosting: a configuração do projeto vem de /__/firebase/init.json (publicada pelo
//    próprio Hosting), sem arquivo nenhum no repositório.
//  - web/js/firebase-config.js (opcional, fora do git): força um projeto, ex. para testar em outro servidor.
//  - Em localhost sem esse arquivo: emuladores locais (firebase emulators:start), projeto demo-alfred.
import { initializeApp } from 'https://www.gstatic.com/firebasejs/13.2.0/firebase-app.js';
import { getAuth, connectAuthEmulator } from 'https://www.gstatic.com/firebasejs/13.2.0/firebase-auth.js';
import {
  initializeFirestore, connectFirestoreEmulator, persistentLocalCache, persistentMultipleTabManager,
} from 'https://www.gstatic.com/firebasejs/13.2.0/firebase-firestore.js';

const LOCAL = ['localhost', '127.0.0.1'].includes(location.hostname);

async function loadConfig() {
  try {
    const mod = await import('./firebase-config.js');
    if (mod.firebaseConfig) return mod.firebaseConfig;
  } catch {
    // arquivo não criado: normal no Firebase Hosting
  }
  if (LOCAL) return null;
  // No Firebase Hosting, a configuração do projeto já é publicada pelo próprio Hosting
  try {
    const res = await fetch('/__/firebase/init.json');
    if (res.ok) return await res.json();
  } catch {
    // sem rede na primeira visita: cai no erro abaixo
  }
  return null;
}

const config = await loadConfig();
export const usingEmulator = !config && LOCAL;

if (!config && !LOCAL) {
  throw new Error('Configuração do Firebase ausente (web/js/firebase-config.js).');
}

export const app = initializeApp(config || {
  // Valores fictícios: só os emuladores locais aceitam (nenhuma chave real no código)
  apiKey: 'demo-key-somente-emulador', projectId: 'demo-alfred', authDomain: 'demo-alfred.firebaseapp.com',
});

export const auth = getAuth(app);
auth.languageCode = 'pt-BR';

// Cache offline persistente (IndexedDB), compartilhado entre abas: funciona sem internet
export const db = initializeFirestore(app, {
  localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
});

if (usingEmulator) {
  connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true });
  connectFirestoreEmulator(db, '127.0.0.1', 8080);
}
