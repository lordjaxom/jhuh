package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.component.HasValue
import com.vaadin.flow.data.binder.BeanValidationBinder
import com.vaadin.flow.data.binder.Binder
import kotlin.reflect.KMutableProperty1

fun <BEAN, FIELDVALUE> HasValue<*, FIELDVALUE>.bind(binder: Binder<BEAN>): Binder.BindingBuilder<BEAN, FIELDVALUE> =
    binder.forField(this)

fun <BEAN, FIELDVALUE> Binder.BindingBuilder<BEAN, FIELDVALUE>.to(property: KMutableProperty1<BEAN, FIELDVALUE>)
        : Binder.Binding<BEAN, FIELDVALUE> =
    bind(property.name) // bind by name so bean validation works

inline fun <reified BEAN> binder() = Binder(BEAN::class.java)
inline fun <reified BEAN> beanValidationBinder() = BeanValidationBinder(BEAN::class.java)