package com.intutec.viveroapp.feature.cashier.data.remote

import com.intutec.viveroapp.feature.cashier.domain.model.CashierPaymentInput

interface CashierPaymentRemoteDataSource {
    suspend fun claim(saleId: String, claimToken: String? = null): RemoteCashierClaimDto
    suspend fun release(saleId: String, claimToken: String): RemoteCashierClaimReleaseDto
    suspend fun confirm(
        saleId: String,
        claimToken: String,
        idempotencyKey: String,
        input: CashierPaymentInput,
    ): RemoteCashierPaymentResultDto
}
