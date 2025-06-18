package de.hinundhergestellt.jhuh.components

import com.vaadin.flow.data.binder.BeanValidationBinder
import com.vaadin.flow.data.binder.Binder
import com.vaadin.flow.data.binder.ValidationException
import kotlin.reflect.KMutableProperty1

fun <BEAN, FIELDVALUE> Binder.BindingBuilder<BEAN, FIELDVALUE>.bind(property: KMutableProperty1<BEAN, FIELDVALUE>)
        : Binder.Binding<BEAN, FIELDVALUE> = bind(property.name) // bind by name so bean validation works

inline fun <reified BEAN> beanValidationBinder() = BeanValidationBinder(BEAN::class.java)