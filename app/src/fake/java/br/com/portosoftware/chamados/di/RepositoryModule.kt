package br.com.portosoftware.chamados.di

import br.com.portosoftware.chamados.core.data.ApplicantRepository
import br.com.portosoftware.chamados.core.data.AuthRepository
import br.com.portosoftware.chamados.core.data.CallRepository
import br.com.portosoftware.chamados.core.data.CatalogRepository
import br.com.portosoftware.chamados.core.data.StockRepository
import br.com.portosoftware.chamados.data.fake.FakeApplicantRepository
import br.com.portosoftware.chamados.data.fake.FakeAuthRepository
import br.com.portosoftware.chamados.data.fake.FakeCallRepository
import br.com.portosoftware.chamados.data.fake.FakeCatalogRepository
import br.com.portosoftware.chamados.data.fake.FakeStockRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Flavor "fake": liga os contratos às implementações em memória. */
@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds fun auth(impl: FakeAuthRepository): AuthRepository

    @Binds fun calls(impl: FakeCallRepository): CallRepository

    @Binds fun catalog(impl: FakeCatalogRepository): CatalogRepository

    @Binds fun applicants(impl: FakeApplicantRepository): ApplicantRepository

    @Binds fun stock(impl: FakeStockRepository): StockRepository
}
