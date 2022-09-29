// Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.codewhisperer

import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonFileName
import software.aws.toolkits.jetbrains.services.codewhisperer.CodeWhispererTestUtil.pythonResponse
import software.aws.toolkits.jetbrains.services.codewhisperer.model.InvocationContext
import software.aws.toolkits.jetbrains.services.codewhisperer.service.CodeWhispererService
import software.aws.toolkits.telemetry.CodewhispererAutomatedTriggerType
import software.aws.toolkits.telemetry.CodewhispererCompletionType
import software.aws.toolkits.telemetry.CodewhispererLanguage
import software.aws.toolkits.telemetry.CodewhispererTriggerType

class CodeWhispererStateTest : CodeWhispererTestBase() {

    @Test
    fun `test CodeWhisperer invocation sets request metadata correctly`() {
        val statesCaptor = argumentCaptor<InvocationContext>()

        withCodeWhispererServiceInvokedAndWait {
            verify(popupManagerSpy).render(statesCaptor.capture(), any(), any())

            val actualRequestContext = statesCaptor.firstValue.requestContext
            val (actualProject, actualEditor, actualTriggerTypeInfo, actualCaretPosition, actualFileContextInfo) = actualRequestContext
            val (actualCaretContext, actualFilename, actualProgrammingLanguage) = actualFileContextInfo

            assertThat(actualProject).isEqualTo(projectRule.project)
            assertThat(actualEditor).isEqualTo(projectRule.fixture.editor)
            assertThat(actualTriggerTypeInfo.triggerType).isEqualTo(CodewhispererTriggerType.OnDemand)
            assertThat(actualTriggerTypeInfo.automatedTriggerType).isEqualTo(CodewhispererAutomatedTriggerType.Unknown)
            assertThat(actualFilename).isEqualTo(pythonFileName)
            assertThat(actualProgrammingLanguage.languageId).isEqualTo(CodewhispererLanguage.Python.toString())

            runInEdtAndWait {
                val editor = projectRule.fixture.editor
                val expectedCurrOffset = editor.caretModel.offset
                val document = editor.document
                val expectedCaretLeftFileContext = document.getText(TextRange(0, expectedCurrOffset))
                val expectedCaretRightFileContext = document.getText(TextRange(expectedCurrOffset, document.textLength))
                val (actualCaretLeftFileContext, actualCaretRightFileContext) = actualCaretContext
                assertThat(actualCaretLeftFileContext).isEqualTo(expectedCaretLeftFileContext)
                assertThat(actualCaretRightFileContext).isEqualTo(expectedCaretRightFileContext)

                val (actualOffset, actualLine) = actualCaretPosition
                assertThat(actualOffset).isEqualTo(expectedCurrOffset)
                assertThat(actualLine).isEqualTo(document.getLineNumber(expectedCurrOffset))
            }
        }
    }

    @Test
    fun `test CodeWhisperer invocation sets response metadata correctly`() {
        val statesCaptor = argumentCaptor<InvocationContext>()

        withCodeWhispererServiceInvokedAndWait {
            verify(popupManagerSpy).render(statesCaptor.capture(), any(), any())

            val actualResponseContext = statesCaptor.firstValue.responseContext
            assertThat(listOf(actualResponseContext.sessionId)).isEqualTo(
                pythonResponse.sdkHttpResponse().headers()[CodeWhispererService.KET_SESSION_ID]
            )
            assertThat(actualResponseContext.completionType).isEqualTo(CodewhispererCompletionType.Block)
        }
    }

    @Test
    fun `test CodeWhisperer invocation sets recommendation metadata correctly`() {
        val statesCaptor = argumentCaptor<InvocationContext>()

        withCodeWhispererServiceInvokedAndWait {
            verify(popupManagerSpy).render(statesCaptor.capture(), any(), any())

            val actualRecommendationContext = statesCaptor.firstValue.recommendationContext
            val (actualDetailContexts, actualUserInput) = actualRecommendationContext

            assertThat(actualUserInput).isEqualTo("")
            val expectedCount = pythonResponse.recommendations().size
            assertThat(actualDetailContexts.size).isEqualTo(expectedCount)
            actualDetailContexts.forEachIndexed { i, actualDetailContext ->
                val (actualRequestId, actualRecommendationDetail, _, actualIsDiscarded) = actualDetailContext
                assertThat(actualRecommendationDetail.content()).isEqualTo(pythonResponse.recommendations()[i].content())
                assertThat(actualRequestId).isEqualTo(pythonResponse.responseMetadata().requestId())
                assertThat(actualIsDiscarded).isEqualTo(false)
            }
        }
    }

    @Test
    fun `test CodeWhisperer invocation sets initial typeahead and selected index correctly`() {
        withCodeWhispererServiceInvokedAndWait {
            val sessionContext = popupManagerSpy.sessionContext
            val actualSelectedIndex = sessionContext.selectedIndex
            val actualTypeahead = sessionContext.typeahead
            val actualTypeaheadOriginal = sessionContext.typeaheadOriginal

            assertThat(actualSelectedIndex).isEqualTo(0)
            assertThat(actualTypeahead).isEqualTo("")
            assertThat(actualTypeaheadOriginal).isEqualTo("")
        }
    }
}
