package ca.devmesh.seerrtv.data

import ca.devmesh.seerrtv.model.CreateIssueRequest
import ca.devmesh.seerrtv.model.Issue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IssueRepository @Inject constructor(
    private val api: SeerrApiService
) {
    suspend fun createIssue(request: CreateIssueRequest): ApiResult<Issue> = api.createIssue(request)
    suspend fun addComment(issueId: Int, message: String): ApiResult<Issue> = api.addIssueComment(issueId, message)
}


