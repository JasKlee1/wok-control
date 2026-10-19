/* Wok Control — comportamiento compartido de la interfaz (sin dependencias externas salvo Bootstrap). */
(function () {
  'use strict';

  // Menú lateral en móviles y tabletas
  document.addEventListener('click', function (e) {
    var app = document.querySelector('.wok-app');
    if (!app) return;
    if (e.target.closest('[data-wok-menu]')) {
      app.classList.toggle('menu-abierto');
    } else if (e.target.closest('.wok-backdrop')) {
      app.classList.remove('menu-abierto');
    }
  });

  // Confirmación para operaciones sensibles: <form data-wok-confirmar="¿Seguro?">
  var formPendiente = null;
  document.addEventListener('submit', function (e) {
    var form = e.target;
    var mensaje = form.getAttribute('data-wok-confirmar');
    if (!mensaje || form.dataset.wokConfirmado === 'si') return;
    var modalEl = document.getElementById('wokModalConfirmar');
    if (!modalEl || typeof bootstrap === 'undefined') {
      if (!window.confirm(mensaje)) e.preventDefault();
      return;
    }
    e.preventDefault();
    formPendiente = form;
    modalEl.querySelector('[data-wok-confirmar-texto]').textContent = mensaje;
    var boton = modalEl.querySelector('[data-wok-confirmar-ok]');
    boton.textContent = form.getAttribute('data-wok-confirmar-boton') || 'Confirmar';
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  });

  document.addEventListener('click', function (e) {
    if (!e.target.closest('[data-wok-confirmar-ok]') || !formPendiente) return;
    var modalEl = document.getElementById('wokModalConfirmar');
    bootstrap.Modal.getOrCreateInstance(modalEl).hide();
    formPendiente.dataset.wokConfirmado = 'si';
    if (formPendiente.requestSubmit) formPendiente.requestSubmit(); else formPendiente.submit();
    formPendiente = null;
  });

  // Evita doble envío de formularios (doble cobro, doble pedido)
  document.addEventListener('submit', function (e) {
    if (e.defaultPrevented) return;
    var form = e.target;
    if (form.hasAttribute('data-wok-permitir-reenvio')) return;
    var btn = form.querySelector('button[type=submit]:not([formnovalidate])');
    if (btn) {
      setTimeout(function () { btn.disabled = true; btn.classList.add('disabled'); }, 0);
    }
  });

  // Búsqueda instantánea en tablas: <input data-wok-buscar="#idTabla">
  document.addEventListener('input', function (e) {
    var sel = e.target.getAttribute && e.target.getAttribute('data-wok-buscar');
    if (!sel) return;
    var q = e.target.value.toLowerCase().trim();
    document.querySelectorAll(sel + ' tbody tr').forEach(function (tr) {
      tr.style.display = tr.textContent.toLowerCase().indexOf(q) >= 0 ? '' : 'none';
    });
  });

  // Ocultar alertas flash de éxito tras unos segundos
  setTimeout(function () {
    document.querySelectorAll('.wok-alerta.alert-success[data-auto-cerrar]').forEach(function (el) {
      if (typeof bootstrap !== 'undefined') bootstrap.Alert.getOrCreateInstance(el).close();
    });
  }, 6000);

  // Recarga periódica opcional: <body data-wok-recargar="30">
  var segundos = parseInt(document.body.getAttribute('data-wok-recargar') || '0', 10);
  if (segundos > 0) {
    setTimeout(function () {
      if (!document.querySelector('.modal.show') && !document.activeElement.matches('input,textarea,select')) {
        window.location.reload();
      }
    }, segundos * 1000);
  }

  // Filas dinámicas en formularios (items de pedido, receta, orden de compra)
  document.addEventListener('click', function (e) {
    var agregar = e.target.closest('[data-wok-agregar-fila]');
    if (agregar) {
      var contenedor = document.querySelector(agregar.getAttribute('data-wok-agregar-fila'));
      var plantilla = document.querySelector(agregar.getAttribute('data-wok-plantilla'));
      if (!contenedor || !plantilla) return;
      var indice = contenedor.querySelectorAll('[data-wok-fila]').length;
      var html = plantilla.innerHTML.replace(/__i__/g, String(indice));
      contenedor.insertAdjacentHTML('beforeend', html);
      return;
    }
    var quitar = e.target.closest('[data-wok-quitar-fila]');
    if (quitar) {
      var fila = quitar.closest('[data-wok-fila]');
      var cont = fila && fila.parentElement;
      if (!fila) return;
      fila.remove();
      // reindexar nombres para que Spring enlace la lista sin huecos
      cont.querySelectorAll('[data-wok-fila]').forEach(function (f, idx) {
        f.querySelectorAll('[name]').forEach(function (inp) {
          inp.name = inp.name.replace(/\[\d+\]/, '[' + idx + ']');
        });
      });
    }
  });
})();
