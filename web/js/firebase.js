// Inicialização do Firebase na web (SDK modular pelo CDN oficial do Google).
//  - Com web/js/firebase-config.js (copiado do console; fica fora do git): projeto real.
//  - Sem ele, abrindo em localhost: emuladores locais (firebase emulators:start), projeto demo-alfred.
import { initializeApp } from 'https://www.gstatic.com/firebasejs/13.2.0/firebase-app.js';
import { getAuth, connectAuthEmulator } from 'https://www.gstatic.com/firebasejs/13.2.0/firebase-auth.js';
import {
  initializeFirestore, connectFirestoreEmulator, persistentLocalCache, persistentMultipleTabManager,
} from 'https://www.gstatic.com/firebasejs/13.2.0/firebase-firestore.js';

const LOCAL = ['localhost', '127.0.0.1'].includes(location.hostname);

async function loadConfig() {
  try {
    const mod = await import('./firebase-config.js');
    return mod.firebaseConfig || null;
  } catch {
    return null; // arquivo ainda não criado
  }
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
