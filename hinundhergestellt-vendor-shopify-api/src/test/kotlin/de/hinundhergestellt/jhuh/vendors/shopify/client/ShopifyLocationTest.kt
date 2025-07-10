package de.hinundhergestellt.jhuh.vendors.shopify.client

import de.hinundhergestellt.jhuh.vendors.shopify.graphql.types.Location
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShopifyLocationTest {

    @Test
    fun `test creation from primary constructor`() {
        val location = ShopifyLocation(
            "LOC1", "Main Store",
            isPrimary = true,
            fulfillsOnlineOrders = false,
            hasActiveInventory = true,
            shipsInventory = false
        )
        assertThat(location.id).isEqualTo("LOC1")
        assertThat(location.name).isEqualTo("Main Store")
        assertThat(location.isPrimary).isTrue()
        assertThat(location.fulfillsOnlineOrders).isFalse()
        assertThat(location.hasActiveInventory).isTrue()
        assertThat(location.shipsInventory).isFalse()
    }

    @Test
    fun `test creation from Location`() {
        val gqlLocation = mockk<Location> {
            every { id } returns "LOC2"
            every { name } returns "Warehouse"
            every { isPrimary } returns false
            every { fulfillsOnlineOrders } returns false
            every { hasActiveInventory } returns false
            every { shipsInventory } returns false
        }
        val location = ShopifyLocation(gqlLocation)
        assertThat(location.id).isEqualTo("LOC2")
        assertThat(location.name).isEqualTo("Warehouse")
        assertThat(location.isPrimary).isFalse()
        assertThat(location.fulfillsOnlineOrders).isFalse()
        assertThat(location.hasActiveInventory).isFalse()
        assertThat(location.shipsInventory).isFalse()
    }

    @Test
    fun `test toString output`() {
        val location = ShopifyLocation(
            "LOC2", "Warehouse",
            isPrimary = false,
            fulfillsOnlineOrders = false,
            hasActiveInventory = false,
            shipsInventory = false
        )
        val expected = "ShopifyLocation(id='LOC2', name='Warehouse', isPrimary=false)"
        assertThat(location.toString()).isEqualTo(expected)
    }
}

