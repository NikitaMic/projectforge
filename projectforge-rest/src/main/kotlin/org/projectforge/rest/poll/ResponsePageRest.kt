package org.projectforge.rest.poll

import com.fasterxml.jackson.databind.ObjectMapper
import org.projectforge.business.poll.PollDO
import org.projectforge.business.poll.PollDao
import org.projectforge.business.poll.PollResponseDO
import org.projectforge.business.poll.PollResponseDao
import org.projectforge.business.user.service.UserService
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.framework.utils.NumberHelper
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractDynamicPageRest
import org.projectforge.rest.core.PagesResolver
import org.projectforge.rest.core.RestResolver
import org.projectforge.rest.dto.FormLayoutData
import org.projectforge.rest.dto.PostData
import org.projectforge.rest.poll.types.*
import org.projectforge.ui.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID
import javax.servlet.http.HttpServletRequest
import org.projectforge.rest.dto.User


@RestController
@RequestMapping("${Rest.URL}/response")
class ResponsePageRest : AbstractDynamicPageRest() {

    @Autowired
    private lateinit var pollDao: PollDao

    @Autowired
    private lateinit var pollResponseDao: PollResponseDao

    @Autowired
    private lateinit var userService: UserService

    @GetMapping("dynamic")
    fun getForm(request: HttpServletRequest, @RequestParam("pollid") pollStringId: String?, @RequestParam("questionOwner") delUser: String?): FormLayoutData {
        val id = NumberHelper.parseInteger(pollStringId) ?: throw IllegalArgumentException("id not given.")

        //used to load awnsers, is an attendee chosen by a fullaccessuser in order to awnser for them or the Threadlocal User
        var questionOwner: Int? = null

        val pollData = pollDao.internalGetById(id) ?: PollDO()

        if (delUser != "null" && pollDao.hasFullAccess(pollData) && pollDao.isAttendee(pollData, delUser?.toInt()))
            questionOwner = delUser?.toInt()
        else
            questionOwner = ThreadLocalUserContext.user?.id


        val pollDto = transformPollFromDB(pollData)

        val layout = UILayout("poll.response.title")
        val fieldSet = UIFieldset(12, title = pollDto.title)
        fieldSet
            .add(UIReadOnlyField(value = pollDto.description, label = "Description"))
            .add(UIReadOnlyField(value = pollDto.location, label = "Location"))
            .add(UIReadOnlyField(value = pollDto.owner?.displayName, label = "Owner"))
             .add(UIReadOnlyField(value = pollDto.deadline.toString(), label = "Deadline"))

        layout.add(fieldSet)

        val pollResponse = PollResponse()
        pollResponse.poll = pollData

        pollResponseDao.internalLoadAll().firstOrNull { response ->
            response.owner?.id  == questionOwner
                    && response.poll?.id == pollData.id
        }?.let {
            pollResponse.copyFrom(it)
        }

        pollDto.inputFields?.forEachIndexed { index, field ->
            val fieldSet2 = UIFieldset(title = field.question)
            val answer = Answer()
            answer.uid = UUID.randomUUID().toString()
            answer.questionUid = field.uid
            pollResponse.responses?.firstOrNull {
                it.questionUid == field.uid
            }.let {
                if (it == null)
                    pollResponse.responses?.add(answer)
            }

            val col = UICol()

            if (field.type == BaseType.TextQuestion) {
                col.add(UITextArea("responses[$index].answers[0]"))
            }
            if (field.type == BaseType.SingleResponseQuestion) {
                col.add(
                    UIRadioButton(
                        "responses[$index].answers[0]",
                        value = field.answers!![0],
                        label = field.answers?.get(0) ?: ""
                    )
                )
                col.add(
                    UIRadioButton(
                        "responses[$index].answers[0]",
                        value = field.answers!![1],
                        label = field.answers?.get(1) ?: ""
                    )
                )
            }
            if (field.type == BaseType.MultiResponseQuestion) {
                field.answers?.forEachIndexed { index2, _ ->
                    col.add(UICheckbox("responses[$index].answers[$index2]", label = field.answers?.get(index2) ?: ""))
                }
            }
            fieldSet2.add(UIRow().add(col))
            layout.add(fieldSet2)
        }

        layout.add(
            UIButton.createDefaultButton(
                id = "addResponse",
                title = "submit",
                responseAction = ResponseAction(
                    RestResolver.getRestUrl(
                        this::class.java,
                        "addResponse"
                    ) + "/?questionOwner=${questionOwner}", targetType = TargetType.POST
                )
            )
        )

        return FormLayoutData(pollResponse, layout, createServerData(request))
    }

    @PostMapping("addResponse")
    fun addResponse(
        request: HttpServletRequest,
        @RequestBody postData: PostData<PollResponse>, @RequestParam("questionOwner") questionOwner: Int?
    ): ResponseEntity<ResponseAction>? {
        val questionOwner: Int? = questionOwner
        val pollResponseDO = PollResponseDO()
        postData.data.copyTo(pollResponseDO)
        pollResponseDO.owner = userService.getUser(questionOwner)
        pollResponseDao.internalLoadAll().firstOrNull { pollResponse ->
            pollResponse.owner?.id == questionOwner
                    && pollResponse.poll?.id == postData.data.poll?.id
        }?.let {
            it.responses = pollResponseDO.responses
            pollResponseDao.update(it)
            return ResponseEntity.ok(
                ResponseAction(
                    targetType = TargetType.REDIRECT,
                    url = PagesResolver.getListPageUrl(PollPageRest::class.java, absolute = true)
                )
            )
        }

        pollResponseDao.saveOrUpdate(pollResponseDO)
        return ResponseEntity.ok(
            ResponseAction(
                targetType = TargetType.REDIRECT,
                url = PagesResolver.getListPageUrl(PollPageRest::class.java, absolute = true)
            )
        )
    }


    private fun transformPollFromDB(obj: PollDO): Poll {
        val poll = Poll()
        poll.copyFrom(obj)
        if (obj.inputFields != null) {
            val a = ObjectMapper().readValue(obj.inputFields, MutableList::class.java)
            poll.inputFields = a.map { Question().toObject(ObjectMapper().writeValueAsString(it)) }.toMutableList()
        }
        return poll
    }
}