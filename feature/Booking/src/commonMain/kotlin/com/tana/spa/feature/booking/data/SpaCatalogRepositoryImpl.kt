package com.tana.spa.feature.booking.data

import com.tana.spa.core.network.NetworkClient
import com.tana.spa.feature.booking.domain.Catalog
import com.tana.spa.feature.booking.domain.Product
import com.tana.spa.feature.booking.domain.SpaCatalogRepository
import com.tana.spa.shared.SupabasePaths
import com.tana.spa.shared.booking.CatalogResponse
import com.tana.spa.shared.booking.ProductDto
import com.tana.spa.shared.booking.ProductsResponse
import com.tana.spa.shared.booking.ServiceCategoryDto
import com.tana.spa.shared.booking.ServiceDto
import com.tana.spa.shared.booking.TherapistDto
import com.tana.spa.shared.spa.Service
import com.tana.spa.shared.spa.ServiceCategory
import com.tana.spa.shared.spa.Therapist
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.http.isSuccess

/**
 * The menu as the server has it. Ids are the server's slugs, so a booking the app sends names a
 * treatment and a therapist the server knows.
 */
class SpaCatalogRepositoryImpl(private val networkClient: NetworkClient, private val baseUrl: String) : SpaCatalogRepository {

    override suspend fun catalog(): Catalog {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_catalog"))
        check(response.status.isSuccess()) { "Catalog request failed with ${response.status.value}" }
        val body = response.body<CatalogResponse>()
        return Catalog(
            services = body.services.map { it.toService() },
            therapists = body.therapists.map { it.toTherapist() },
            todayEpochDay = body.todayEpochDay,
        )
    }

    override suspend fun products(): List<Product> {
        val response = networkClient.httpClient.post(baseUrl + SupabasePaths.rpc("spa_products"))
        check(response.status.isSuccess()) { "Products request failed with ${response.status.value}" }
        return response.body<ProductsResponse>().products.map { it.toProduct() }
    }
}

private fun ProductDto.toProduct() = Product(
    id = slug,
    name = name,
    brand = brand,
    description = description,
    imageUrl = imageUrl,
    priceVnd = price,
    size = size,
)

private fun ServiceDto.toService() = Service(
    id = slug,
    name = name,
    minutes = minutes,
    priceVnd = price,
    category = category.toCategory(),
    includes = includes,
)

private fun ServiceCategoryDto.toCategory() = when (this) {
    ServiceCategoryDto.MASSAGE -> ServiceCategory.MASSAGE
    ServiceCategoryDto.FACIAL -> ServiceCategory.FACIAL
    ServiceCategoryDto.BODY -> ServiceCategory.BODY
}

private fun TherapistDto.toTherapist() = Therapist(id = slug, name = name, focus = focus, nextSlot = nextSlot)
