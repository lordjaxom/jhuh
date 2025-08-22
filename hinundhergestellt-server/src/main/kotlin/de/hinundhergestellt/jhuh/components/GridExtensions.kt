@file:OptIn(ExperimentalContracts::class)

package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.ComponentEventListener
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.grid.ClientItemToggleEvent
import com.vaadin.flow.component.grid.ColumnTextAlign
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.GridMultiSelectionModel
import com.vaadin.flow.component.html.Span
import com.vaadin.flow.component.icon.AbstractIcon
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode
import com.vaadin.flow.component.orderedlayout.HorizontalLayout
import com.vaadin.flow.data.renderer.ComponentRenderer
import com.vaadin.flow.dom.Style
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs
import kotlin.math.min
import kotlin.streams.asSequence

@VaadinDsl
fun <T> Grid<T>.componentColumn(
    componentProvider: (T) -> Component,
    block: (@VaadinDsl Grid.Column<T>).() -> Unit = {}
): Grid.Column<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return addComponentColumn(componentProvider).apply { isSortable = false; block() }
}

@VaadinDsl
fun <T, V> Grid<T>.textColumn(
    valueProvider: (T) -> V,
    block: (@VaadinDsl Grid.Column<T>).() -> Unit = {}
): Grid.Column<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return addColumn(valueProvider).apply { isSortable = false; block() }
}

@VaadinDsl
fun <T> Grid<T>.ellipsisColumn(
    valueProvider: (T) -> String,
    block: (@VaadinDsl Grid.Column<T>).() -> Unit = {}
): Grid.Column<T> {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return addColumn(ComponentRenderer { item ->
        val value = valueProvider(item)
        Span(value).apply {
            className = "ellipsis-cell"
            element.setAttribute("data-fulltext", value)
            addAttachListener {
                ui.getOrNull()?.page?.executeJs("""
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
                    """.trimIndent())
            }
        }
    }).apply { isSortable = false; block() }
}

fun <T, V> Grid<T>.textColumn(header: String, flexGrow: Int = 1, valueProvider: (T) -> V): Grid.Column<T> =
    addColumn(valueProvider)
        .setHeader(header)
        .also {
            it.isSortable = false
            it.flexGrow = flexGrow
        }

fun <T, V> Grid<T>.countColumn(header: String = "#", valueProvider: (T) -> V): Grid.Column<T> =
    addColumn(valueProvider)
        .setHeader(header)
        .also {
            it.isSortable = false
            it.textAlign = ColumnTextAlign.END
            it.width = "4em"
            it.flexGrow = 0
        }

fun <T> Grid<T>.actionsColumn(count: Int, actionsProvider: (T) -> List<Button>): Grid.Column<T> =
    addComponentColumn { buildActionsLayout(count, actionsProvider(it)) }
        .setHeader("")
        .also {
            it.isSortable = false
            it.width = "${count * 30 + 16}px"
            it.flexGrow = 0
        }

fun <T> Grid<T>.iconColumn(iconProvider: (T) -> AbstractIcon<*>): Grid.Column<T> =
    addComponentColumn(iconProvider)
        .setHeader("")
        .apply {
            isSortable = false
            width = "32px"
            flexGrow = 0
        }

inline fun <reified T> Grid<T>.rangeMultiSelectionMode() {
    val selectionModel = setSelectionMode(Grid.SelectionMode.MULTI) as GridMultiSelectionModel<T>
    selectionModel.addClientItemToggleListener(object : ComponentEventListener<ClientItemToggleEvent<T>> {
        private var rangeStartItem: T? = null
        override fun onComponentEvent(event: ClientItemToggleEvent<T>) {
            if (rangeStartItem == null) rangeStartItem = event.item
            if (event.isShiftKey) {
                val rangeStart = listDataView.getItemIndex(rangeStartItem).get()
                val rangeEnd = listDataView.getItemIndex(event.item).get()
                val rangeItems = listDataView.items.asSequence()
                    .drop(min(rangeStart, rangeEnd))
                    .take(abs(rangeStart - rangeEnd))
                    .toList().toTypedArray()
                if (event.isSelected) selectionModel.selectItems(*rangeItems)
                else selectionModel.deselectItems(*rangeItems)
            }
            rangeStartItem = event.item
        }
    })
}

private fun buildActionsLayout(count: Int, components: List<Button>) =
    HorizontalLayout().apply {
        isSpacing = false
        width = "${count * 30}px"
        justifyContentMode = JustifyContentMode.END
        style.setWhiteSpace(Style.WhiteSpace.NOWRAP)
        add(components)
    }