package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.Text
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.notification.Notification
import com.vaadin.flow.component.notification.NotificationVariant
import com.vaadin.flow.component.orderedlayout.FlexComponent
import com.vaadin.flow.component.orderedlayout.HorizontalLayout

fun showErrorNotification(error: Throwable) {
    val notification = Notification()
    notification.position = Notification.Position.TOP_CENTER
    notification.duration = Int.Companion.MAX_VALUE
    notification.addThemeVariants(NotificationVariant.LUMO_ERROR)
    val button = Button(VaadinIcon.CLOSE_SMALL.create()) { _ -> notification.close() }
    button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE)
    val layout = HorizontalLayout(VaadinIcon.WARNING.create(), Text(error.message), button)
    layout.alignItems = FlexComponent.Alignment.CENTER
    notification.add(layout)
    notification.open()
}
