// Rede de segurança da tela inicial: se o app não abrir (erro ao carregar o código, rede
// bloqueada, extensão do navegador...), mostra o motivo e um botão para recarregar, em vez
// de ficar parado em "Às suas ordens…". Script comum (não módulo): roda mesmo se o módulo falhar.
(function () {
  var shown = false;

  function show(reason) {
    var boot = document.getElementById('boot');
    if (shown || !boot || boot.hidden) return;
    shown = true;
    var msg = document.createElement('p');
    msg.className = 'error';
    msg.textContent = 'Não consegui abrir o Alfred' + (reason ? ': ' + reason : '.');
    var tip = document.createElement('p');
    tip.className = 'caption';
    tip.textContent = 'Verifique a internet e recarregue. Se continuar, desative bloqueadores de anúncio neste site.';
    var btn = document.createElement('button');
    btn.className = 'primary';
    btn.type = 'button';
    btn.textContent = 'Recarregar';
    btn.onclick = function () { location.reload(); };
    boot.append(msg, tip, btn);
  }

  window.addEventListener('error', function (e) { show(e.message); });
  window.addEventListener('unhandledrejection', function (e) {
    show(e.reason && e.reason.message ? e.reason.message : String(e.reason));
  });
  // Sem erro visível, mas ainda parado depois de 15 s (ex.: o CDN do Firebase não respondeu)
  setTimeout(function () { show('demorou demais para carregar'); }, 15000);
})();
