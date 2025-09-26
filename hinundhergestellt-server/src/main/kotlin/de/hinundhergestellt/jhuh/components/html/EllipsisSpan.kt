@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components.html

import com.vaadin.flow.component.HasComponents
import com.vaadin.flow.component.html.Span
import de.hinundhergestellt.jhuh.components.VaadinDsl
import de.hinundhergestellt.jhuh.components.init
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.optionals.getOrNull

class EllipsisSpan(text: String? = null) : Span(text) {

    init {
        className = "ellipsis-cell"
        element.setAttribute("data-fulltext", text)
        addAttachListener {
            ui.getOrNull()?.page?.executeJs(
                """
                if (!window.__ellipsisHoverOverlay__) {
                  window.__ellipsisHoverOverlay__ = (function(){
                    const margin = 8;               // Abstand zum Rand
                    const maxWidth = 800;           // harte Obergrenze
                    const zIndex = 10000;
                
                    let overlay, currTarget;
                
                    function ensureOverlay(){
                      if (overlay) return overlay;
                      overlay = document.createElement('div');
                      overlay.style.position = 'fixed';
                      overlay.style.zIndex = String(zIndex);
                      overlay.style.display = 'none';
                      overlay.style.boxSizing = 'border-box';
                      overlay.style.padding = '8px 10px';
                      overlay.style.borderRadius = '8px';
                      overlay.style.boxShadow = '0 8px 24px rgba(0,0,0,.2)';
                      overlay.style.background = 'var(--lumo-base-color, #fff)';
                      overlay.style.color = 'var(--lumo-body-text-color, #1e1e1e)';
                      overlay.style.border = '1px solid var(--lumo-contrast-10pct, rgba(0,0,0,.08))';
                      overlay.style.whiteSpace = 'normal';  // umbrechen
                      overlay.style.overflow = 'auto';      // ggf. nach unten scrollen
                      overlay.style.pointerEvents = 'none';
                      document.body.appendChild(overlay);
                      return overlay;
                    }
                
                    function copyTextStyle(from, to){
                      const cs = getComputedStyle(from);
                      [
                        'font','fontFamily','fontSize','fontWeight','fontStyle','lineHeight',
                        'letterSpacing','wordSpacing','textTransform','textDecoration',
                        'direction','textAlign'
                      ].forEach(p => to.style[p] = cs[p]);
                    }
                
                    function onEnter(e){
                      const target = e.currentTarget;
                      currTarget = target;
                
                      // Nur anzeigen, wenn wirklich abgeschnitten (…)
                      if (target.scrollWidth <= target.clientWidth) return;
                
                      const rect = target.getBoundingClientRect();
                      const vw = window.innerWidth, vh = window.innerHeight;
                
                      const ov = ensureOverlay();
                      ov.textContent = target.getAttribute('data-fulltext') || target.textContent || '';
                      copyTextStyle(target, ov);
                
                      // Breite: bis zum rechten Rand (oder maxWidth)
                      const availRight = Math.max(0, vw - rect.left - margin);
                      const width = Math.min(availRight, maxWidth);
                      ov.style.width = width + 'px';
                
                      // Position: links bündig mit Zelle, oberkante = Zellenoberkante
                      let left = rect.left, top = rect.top;
                
                      // Höhe: so viel wie bis unten Platz ist
                      const availDown = Math.max(0, vh - rect.bottom - margin);
                      const maxHeight = Math.max(availDown, rect.height); // min. Zellenhöhe
                      ov.style.maxHeight = maxHeight + 'px';
                
                      ov.style.left = left + 'px';
                      ov.style.top  = top  + 'px';
                      ov.style.display = 'block';
                
                      // Wenn der Inhalt höher als verfügbar ist, lassen wir scrollbar stehen.
                    }
                
                    function onLeave(){
                      currTarget = null;
                      if (overlay) overlay.style.display = 'none';
                    }
                
                    function onScrollOrResize(){
                      if (overlay && overlay.style.display !== 'none') {
                        // Bei Scroll/Resize ausblenden – vermeidet „Wackler“
                        overlay.style.display = 'none';
                      }
                    }
                
                    function delegate(root=document){
                      root.addEventListener('mouseenter', function(evt){
                        const el = evt.target.closest && evt.target.closest('.ellipsis-cell');
                        if (el) onEnter({currentTarget: el});
                      }, true);
                      root.addEventListener('mouseleave', function(evt){
                        const el = evt.target.closest && evt.target.closest('.ellipsis-cell');
                        if (el) onLeave();
                      }, true);
                      window.addEventListener('scroll', onScrollOrResize, true);
                      window.addEventListener('resize', onScrollOrResize, true);
                    }
                
                    delegate(document);
                    return true;
                  })();
                }
                """.trimIndent()
            )
        }
    }
}

@VaadinDsl
inline fun (@VaadinDsl HasComponents).ellipsisSpan(text: String? = null, block: (@VaadinDsl EllipsisSpan).() -> Unit = {}): Span {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return init(EllipsisSpan(text), block)
}
