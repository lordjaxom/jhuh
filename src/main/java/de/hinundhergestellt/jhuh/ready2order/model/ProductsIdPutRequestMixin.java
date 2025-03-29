package de.hinundhergestellt.jhuh.ready2order.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

@SuppressWarnings("unused")
public abstract class ProductsIdPutRequestMixin {

    @JsonIgnore
    public abstract Integer getProductVatId();
}
