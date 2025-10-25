package com.kiz.transitapp.data.database.dao

import androidx.room.*
import com.kiz.transitapp.data.database.entities.Agency

@Dao
interface AgencyDao {
    @Query("SELECT * FROM agency")
    suspend fun getAllAgencies(): List<Agency>

    @Query("SELECT * FROM agency WHERE agency_id = :agencyId")
    suspend fun getAgencyById(agencyId: String): Agency?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(agencies: List<Agency>)

    @Query("DELETE FROM agency")
    suspend fun deleteAll()
}
