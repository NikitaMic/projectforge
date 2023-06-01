package org.projectforge.business.poll

import org.hibernate.search.annotations.Indexed
import org.projectforge.business.poll.filter.PollAssignment
import org.projectforge.business.poll.filter.PollState
import org.projectforge.common.anots.PropertyInfo
import org.projectforge.framework.persistence.api.AUserRightId
import org.projectforge.framework.persistence.entities.DefaultBaseDO
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.springframework.context.annotation.DependsOn
import java.time.LocalDate
import javax.persistence.*


@Suppress("UNREACHABLE_CODE")
@Entity
@Indexed
@Table(name = "t_poll")
@AUserRightId(value = "poll", checkAccess = false)
@DependsOn("org.projectforge.framework.persistence.user.entities.PFUserDO")
open class PollDO : DefaultBaseDO() {

    @PropertyInfo(i18nKey = "poll.title")
    @get:Column(name = "title", nullable = false, length = 1000)
    open var title: String? = null

    @PropertyInfo(i18nKey = "poll.description")
    @get:Column(name = "description", length = 10000)
    open var description: String? = null

    @get:PropertyInfo(i18nKey = "poll.owner")
    @get:ManyToOne(fetch = FetchType.LAZY)
    @get:JoinColumn(name = "owner_fk", nullable = false)
    open var owner: PFUserDO? = null

    @PropertyInfo(i18nKey = "poll.location")
    @get:Column(name = "location")
    open var location: String? = null

    @PropertyInfo(i18nKey = "poll.deadline")
    @get:Column(name = "deadline", nullable = false)
    open var deadline: LocalDate? = null

    @PropertyInfo(i18nKey = "poll.date")
    @get:Column(name = "date")
    open var date: LocalDate? = null

    @PropertyInfo(i18nKey = "poll.attendees")
    @get:Column(name = "attendeeIds", nullable = true)
    open var attendeeIds: String? = null

    @PropertyInfo(i18nKey = "poll.attendee_groups")
    @get:Column(name = "groupAttendeeIds", nullable = true)
    open var groupAttendeeIds: String? = null

    @PropertyInfo(i18nKey = "poll.full_access_groups")
    @get:Column(name = "full_access_group_ids", length = 4000, nullable = true)
    open var fullAccessGroupIds: String? = null

    @PropertyInfo(i18nKey = "poll.full_access_user")
    @get:Column(name = "full_access_user_ids", length = 4000, nullable = true)
    open var fullAccessUserIds: String? = null

    @PropertyInfo(i18nKey = "poll.inputFields")
    @get:Column(name = "inputFields", length = 1000)
    open var inputFields: String? = null

    @PropertyInfo(i18nKey = "poll.state")
    @get:Column(name = "state", nullable = false)
    open var state: State = State.RUNNING

    @Transient
    fun getPollAssignment(): MutableList<PollAssignment> {
        val currentUserId = ThreadLocalUserContext.userId!!
        val assignmentList = mutableListOf<PollAssignment>()
        if (currentUserId == this.owner?.id) {
            assignmentList.add(PollAssignment.OWNER)
        }
        if (this.fullAccessUserIds != null) {
            val accessUserIds = this.fullAccessUserIds!!.split(", ").map { it.toInt() }.toIntArray()
            if (accessUserIds.contains(currentUserId)) {
                assignmentList.add(PollAssignment.ACCESS)
            }
        }
        if (this.attendeeIds != null) {
            val attendeeUserIds = this.attendeeIds!!.split(", ").map { it.toInt() }.toIntArray()
            if (attendeeUserIds.contains(currentUserId)) {
                assignmentList.add(PollAssignment.ATTENDEE)
            }
        }
        if (assignmentList.isEmpty())
            assignmentList.add(PollAssignment.OTHER)

        return assignmentList
    }

    @Transient
    fun getPollStatus(): PollState {
        //TODO: Maybe change this to enum class State

        return if (this.state == State.FINISHED) {
            PollState.FINISHED
        } else {
            PollState.RUNNING
        }
    }

    enum class State {
        RUNNING, FINISHED
    }
}