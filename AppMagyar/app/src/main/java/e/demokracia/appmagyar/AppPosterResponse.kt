package e.demokracia.appmagyar

data class AppPosterResponse(
    val posted_for_this_email: String?,
    val email: String,
    val errors: List<Error>
)

data class Error(
    val related_to: String,
    val positioned_errors: List<PositionedError>
)

data class PositionedError(
    val code: String,
    val message: String
)
