package de.hinundhergestellt.jhuh.vendors.ready2order.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

@SuppressWarnings("unused")
public abstract class ProductsPostRequestMixin {

    @JsonIgnore
    public abstract Integer getProductVatId();

    @JsonIgnore
    public abstract Integer getProductgroupId();
}
