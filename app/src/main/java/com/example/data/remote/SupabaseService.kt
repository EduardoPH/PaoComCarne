package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseService {

    @GET("rest/v1/hosters?select=*&order=name.asc")
    suspend fun getHosters(): Response<List<SupabaseHosterDto>>

    @POST("rest/v1/hosters")
    @Headers("Prefer: return=representation, resolution=merge-duplicates")
    suspend fun upsertHoster(
        @Body hoster: SupabaseHosterDto
    ): Response<List<SupabaseHosterDto>>

    @PATCH("rest/v1/hosters")
    @Headers("Prefer: return=minimal")
    suspend fun updateHoster(
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ResponseBody>

    @DELETE("rest/v1/hosters")
    suspend fun deleteHoster(
        @Query("id") idFilter: String
    ): Response<ResponseBody>

    @GET("rest/v1/shifts?select=*&order=opened_at.desc")
    suspend fun getShifts(): Response<List<SupabaseShiftDto>>

    @POST("rest/v1/shifts")
    @Headers("Prefer: return=representation, resolution=merge-duplicates")
    suspend fun upsertShift(
        @Body shift: SupabaseShiftDto
    ): Response<List<SupabaseShiftDto>>

    @PATCH("rest/v1/shifts")
    @Headers("Prefer: return=minimal")
    suspend fun patchShift(
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ResponseBody>

    @GET("rest/v1/attendances?select=*&order=created_at.desc")
    suspend fun getAttendances(): Response<List<SupabaseAttendanceDto>>

    @POST("rest/v1/attendances")
    @Headers("Prefer: return=representation, resolution=merge-duplicates")
    suspend fun upsertAttendance(
        @Body attendance: SupabaseAttendanceDto
    ): Response<List<SupabaseAttendanceDto>>

    @PATCH("rest/v1/attendances")
    @Headers("Prefer: return=minimal")
    suspend fun patchAttendance(
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ResponseBody>

    @GET("rest/v1/attendance_members?select=*&order=created_at.asc")
    suspend fun getAttendanceMembers(): Response<List<SupabaseMemberDto>>

    @POST("rest/v1/attendance_members")
    @Headers("Prefer: return=representation, resolution=merge-duplicates")
    suspend fun upsertMembers(
        @Body members: List<SupabaseMemberDto>
    ): Response<List<SupabaseMemberDto>>

    @POST("rest/v1/attendance_members")
    @Headers("Prefer: return=minimal, resolution=merge-duplicates")
    suspend fun upsertMember(
        @Body member: SupabaseMemberDto
    ): Response<ResponseBody>

    @DELETE("rest/v1/attendance_members")
    suspend fun deleteMembersForAttendance(
        @Query("attendance_id") filter: String
    ): Response<ResponseBody>

    @POST("rest/v1/attendance_audit")
    @Headers("Prefer: return=minimal")
    suspend fun insertAudit(
        @Body audit: SupabaseAuditDto
    ): Response<ResponseBody>

    @GET("rest/v1/queue_events?select=*&order=created_at.desc")
    suspend fun getQueueEvents(): Response<List<SupabaseQueueDto>>

    @POST("rest/v1/queue_events")
    @Headers("Prefer: return=minimal")
    suspend fun insertQueueEvent(
        @Body queue: SupabaseQueueDto
    ): Response<ResponseBody>
}
