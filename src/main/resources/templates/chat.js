hljs.registerAliases(['arduino', 'ino'], { languageName: 'cpp' });
hljs.highlightAll();
decorateAllCodeBlocks();

/**
 * Create (or reset) the streaming placeholder block.
 * Called from Java when the model starts generating.
 */
function beginStreaming() {
    var existing = document.getElementById('streaming-message');
    if (existing) {
        existing.remove();
    }
    var el = document.createElement('div');
    el.id = 'streaming-message';
    el.className = 'streaming';
    document.body.appendChild(el);
}

/**
 * Update the streaming placeholder with new content.
 * The content is a full HTML fragment rendered from Markdown
 * on the Java side.
 */
function updateStreamingMessage(html) {
    var el = document.getElementById('streaming-message');
    if (!el) {
        // defensive: create if not present
        beginStreaming();
        el = document.getElementById('streaming-message');
    }
    el.innerHTML = html;
    el.querySelectorAll('pre code').forEach(function (block) {
        hljs.highlightElement(block);
    });
    decorateAllCodeBlocks(el);
}

/**
 * Copies text to the clipboard. Prefers the async Clipboard API when
 * available (secure contexts), falls back to execCommand('copy') for
 * plain JavaFX WebView where navigator.clipboard is undefined.
 */
function copyTextToClipboard(text) {
    if (navigator.clipboard && window.isSecureContext) {
        navigator.clipboard.writeText(text);
        return true;
    }
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.top = '-9999px';
    ta.setAttribute('readonly', '');
    document.body.appendChild(ta);
    ta.select();
    var ok = false;
    try {
        ok = document.execCommand('copy');
    } catch (e) {
        ok = false;
    }
    document.body.removeChild(ta);
    return ok;
}

/**
 * Adds a "Copy" button to a single <pre> block. Idempotent: marks
 * the block so a second call is a no-op.
 */
function decorateCodeBlock(pre) {
    if (pre.dataset.copyDecorated === 'yes') return;
    pre.dataset.copyDecorated = 'yes';

    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'copy-btn';
    btn.textContent = 'Copy';
    btn.setAttribute('aria-label', 'Copy code');

    btn.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        var code = pre.querySelector('code');
        var text = code ? code.textContent : pre.textContent;
        if (copyTextToClipboard(text)) {
            btn.textContent = 'Copied';
            btn.classList.add('copied');
            setTimeout(function () {
                btn.textContent = 'Copy';
                btn.classList.remove('copied');
            }, 1500);
        }
    });

    pre.appendChild(btn);
}

/**
 * Adds the copy button to every <pre> in the given root (or document).
 * Safe to call repeatedly — decorateCodeBlock is idempotent per block.
 */
function decorateAllCodeBlocks(root) {
    (root || document).querySelectorAll('pre').forEach(decorateCodeBlock);
}


/* -------------------------------------------------------------- *
 *  Message actions: hover panel + context menu                    *
 * -------------------------------------------------------------- */

var _menuEl = null;
var _menuTargetId = null;

/**
 * Returns the .message element containing the given event target,
 * or null if the click landed outside any message.
 */
function findMessageEl(target) {
    if (!target || !target.closest) return null;
    return target.closest('.message');
}

/**
 * Builds the hidden hover-panel with action buttons. Attached to
 * each .message by decorateMessage(). Buttons only log for now —
 * they will call into window.javaBridge once the corresponding
 * methods land in ChatJsBridge.
 */
function buildActionsPanel() {
    var panel = document.createElement('div');
    panel.className = 'msg-actions';

    var edit = document.createElement('button');
    edit.type = 'button';
    edit.className = 'msg-action-btn';
    edit.textContent = '✎';
    edit.title = 'Edit';
    edit.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        if (document.body.dataset.busy === 'true') return;
        var msg = findMessageEl(e.target);
        if (!msg) return;
        if (!window.javaBridge) {
            console.error('javaBridge not available');
            return;
        }
        window.javaBridge.editUserMessage(msg.dataset.msgId);
    });
    panel.appendChild(edit);

    var del = document.createElement('button');
    del.type = 'button';
    del.className = 'msg-action-btn';
    del.textContent = '×';
    del.title = 'Delete';
    del.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        if (document.body.dataset.busy === 'true') return;
        var msg = findMessageEl(e.target);
        if (!msg) return;
        if (!window.javaBridge) {
            console.error('javaBridge not available');
            return;
        }
        window.javaBridge.deleteMessage(msg.dataset.msgId);
    });
    panel.appendChild(del);

    return panel;
}

/**
 * Adds the action panel to a single message. Idempotent per element.
 * Skips SYSTEM messages entirely; shows Edit only when the message
 * carries data-msg-editable="true".
 */
function decorateMessage(msgEl) {
    if (msgEl.dataset.decorated === 'yes') return;
    msgEl.dataset.decorated = 'yes';

    if (msgEl.dataset.msgRole === 'SYSTEM') return;

    var panel = buildActionsPanel();

    if (msgEl.dataset.msgEditable !== 'true') {
        // Hide edit button — only delete is available for this message
        panel.firstChild.style.display = 'none';
    }

    msgEl.appendChild(panel);
}

function decorateAllMessages(root) {
    (root || document).querySelectorAll('.message').forEach(decorateMessage);
}

/* -------- Context menu -------- */

function ensureMenu() {
    if (_menuEl) return _menuEl;

    _menuEl = document.createElement('div');
    _menuEl.className = 'msg-context-menu';

    var del = document.createElement('div');
    del.className = 'msg-context-menu-item';
    del.textContent = 'Delete';

    del.addEventListener('click', function () {
        var id = _menuTargetId;
        hideMenu();
        if (!id) return;
        if (!window.javaBridge) {
            console.error('javaBridge not available');
            return;
        }
        window.javaBridge.deleteMessage(id);
    });

    var edit = document.createElement('div');
    edit.className = 'msg-context-menu-item';
    edit.textContent = 'Edit…';
    edit.addEventListener('click', function () {
        var id = _menuTargetId;
        hideMenu();
        if (!id) return;
        if (!window.javaBridge) {
            console.error('javaBridge not available');
            return;
        }
        window.javaBridge.editUserMessage(id);
    });

    var copy = document.createElement('div');
    copy.className = 'msg-context-menu-item';
    copy.textContent = 'Copy as Markdown';
    copy.addEventListener('click', function () {
        var msg = document.querySelector(
            '.message[data-msg-id="' + _menuTargetId + '"]');
        if (msg) {
            var clone = msg.cloneNode(true);
            clone.querySelectorAll('.msg-actions').forEach(function (n) {
                n.remove();
            });
            copyTextToClipboard(clone.innerText);
        }
        hideMenu();
    });

    _menuEl.appendChild(del);
    _menuEl.appendChild(edit);
    _menuEl.appendChild(copy);
    document.body.appendChild(_menuEl);
    return _menuEl;
}

function showMenu(x, y, msgId, editable) {
    var menu = ensureMenu();
    _menuTargetId = msgId;

    // Edit item is index 1; hide it when not applicable
    var items = menu.querySelectorAll('.msg-context-menu-item');
    items[1].style.display = editable ? 'block' : 'none';

    menu.classList.add('visible');

    // Flip near viewport edges
    var rect = menu.getBoundingClientRect();
    var left = Math.min(x, window.innerWidth - rect.width - 4);
    var top = Math.min(y, window.innerHeight - rect.height - 4);
    menu.style.left = left + 'px';
    menu.style.top = top + 'px';
}

function hideMenu() {
    if (_menuEl) _menuEl.classList.remove('visible');
    _menuTargetId = null;
}

document.addEventListener('contextmenu', function (e) {
    var msg = findMessageEl(e.target);
    if (!msg) {
        hideMenu();
        return;
    }
    e.preventDefault();
    showMenu(e.clientX, e.clientY, msg.dataset.msgId,
        msg.dataset.msgEditable === 'true');
});

document.addEventListener('click', function (e) {
    if (_menuEl && !_menuEl.contains(e.target)) hideMenu();
});

document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') hideMenu();
});

function setBusy(busy) {
    document.body.dataset.busy = busy ? 'true' : 'false';
}

/**
 * Returns true if the user is scrolled to (or near) the bottom.
 */
function isAtBottom() {
    return (window.innerHeight + window.scrollY) >= (document.body.scrollHeight - 50);
}

/**
 * Scroll to the bottom of the page.
 */
function scrollToBottom() {
    window.scrollTo(0, document.body.scrollHeight);
}