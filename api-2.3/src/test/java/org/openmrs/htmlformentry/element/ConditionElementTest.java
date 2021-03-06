package org.openmrs.htmlformentry.element;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.openmrs.*;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ConditionService;
import org.openmrs.api.context.Context;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.htmlformentry.ConditionElement;
import org.openmrs.module.htmlformentry.FormEntryContext;
import org.openmrs.module.htmlformentry.FormEntryContext.Mode;
import org.openmrs.module.htmlformentry.FormEntrySession;
import org.openmrs.module.htmlformentry.FormSubmissionError;
import org.openmrs.module.htmlformentry.widget.ConceptSearchAutocompleteWidget;
import org.openmrs.module.htmlformentry.widget.DateWidget;
import org.openmrs.module.htmlformentry.widget.RadioButtonsWidget;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.mock.web.MockHttpServletRequest;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Context.class)
public class ConditionElementTest {
	
	private ConditionElement element;
	
	private MockHttpServletRequest request;
	
	@Mock
	private MessageSourceService messageSourceService;
	
	@Mock
	private ConditionService conditionService;
	
	@Mock
	private ConceptService conceptService;
	
	@Mock
	private FormEntrySession session;
	
	@Mock
	private FormEntryContext context;
	
	@Mock
	private AdministrationService adminService;
	
	@Mock
	private ConceptSearchAutocompleteWidget conditionSearchWidget;
	
	@Mock
	private RadioButtonsWidget conditionStatusesWidget;
	
	@Mock
	private DateWidget endDateWidget;
	
	@Mock
	private DateWidget onsetDateWidget;
	
	private Encounter encounter;
	
	@Before
	public void setup() {
		// Stub services
		mockStatic(Context.class);
		when(Context.getConditionService()).thenReturn(conditionService);
		when(Context.getMessageSourceService()).thenReturn(messageSourceService);
		when(Context.getConceptService()).thenReturn(conceptService);
		when(Context.getAdministrationService()).thenReturn(adminService);
		
		doAnswer(new Answer<Concept>() {
			
			@Override
			public Concept answer(InvocationOnMock invocation) throws Throwable {
				return new Concept((Integer) invocation.getArguments()[0]);
			}
			
		}).when(conceptService).getConcept(any(Integer.class));
		
		doAnswer(new Answer<ConceptClass>() {
			
			@Override
			public ConceptClass answer(InvocationOnMock invocation) throws Throwable {
				ConceptClass conceptClass = new ConceptClass();
				conceptClass.setName((String) invocation.getArguments()[0]);
				return conceptClass;
			}
			
		}).when(conceptService).getConceptClassByName(any(String.class));
		
		// Setup html form session context
		when(context.getMode()).thenReturn(Mode.ENTER);
		request = new MockHttpServletRequest();
		when(session.getContext()).thenReturn(context);
		when(session.getEncounter()).thenReturn(new Encounter());
		when(session.getPatient()).thenReturn(new Patient(1));
		
		when(onsetDateWidget.getValue(context, request))
		        .thenReturn(new GregorianCalendar(2014, Calendar.FEBRUARY, 11).getTime());
		
		// setup condition element
		element = spy(new ConditionElement());
		element.setConditionSearchWidget(conditionSearchWidget);
		element.setConditionStatusesWidget(conditionStatusesWidget);
		element.setOnSetDateWidget(onsetDateWidget);
		element.setEndDateWidget(endDateWidget);
		encounter = session.getEncounter();
	}
	
	@Test
	public void handleSubmission_shouldCreateNewCondition() {
		// setup
		when(conditionSearchWidget.getValue(context, request)).thenReturn("1519");
		when(conditionStatusesWidget.getValue(context, request)).thenReturn("active");
		
		// replay
		element.handleSubmission(session, request);
		
		// verify		
		Set<Condition> conditions = encounter.getConditions();
		Assert.assertEquals(1, conditions.size());
		
		Condition condition = conditions.iterator().next();
		Assert.assertEquals(ConditionClinicalStatus.ACTIVE, condition.getClinicalStatus());
		Assert.assertThat(condition.getCondition().getCoded().getId(), is(1519));
		
	}
	
	@Test
	public void handleSubmission_shouldCreateInactiveCondition() {
		// setup
		when(conditionSearchWidget.getValue(context, request)).thenReturn("1519");
		GregorianCalendar endDate = new GregorianCalendar(2018, Calendar.DECEMBER, 1);
		when(endDateWidget.getValue(context, request)).thenReturn(endDate.getTime());
		when(conditionStatusesWidget.getValue(context, request)).thenReturn("inactive");
		
		// replay
		element.handleSubmission(session, request);
		
		// verify
		Set<Condition> conditions = encounter.getConditions();
		Assert.assertEquals(1, conditions.size());
		
		Condition condition = conditions.iterator().next();
		Assert.assertEquals(ConditionClinicalStatus.INACTIVE, condition.getClinicalStatus());
		Assert.assertEquals(endDate.getTime(), condition.getEndDate());
		Assert.assertThat(condition.getCondition().getCoded().getId(), is(1519));
	}
	
	@Test
	public void handleSubmission_shouldSupportNoneCodedConceptValues() {
		// setup
		request.addParameter("condition-field-name", "Typed in non-coded value");
		when(context.getFieldName(conditionSearchWidget)).thenReturn("condition-field-name");
		when(conditionSearchWidget.getValue(context, request)).thenReturn("");
		
		// replay
		element.handleSubmission(session, request);
		
		// verify
		Set<Condition> conditions = encounter.getConditions();
		Assert.assertEquals(1, conditions.size());
		
		Condition condition = conditions.iterator().next();
		Assert.assertEquals("Typed in non-coded value", condition.getCondition().getNonCoded());
	}
	
	@Test
	public void handleSubmission_shouldNotAttemptSavingWhenNoConditionWasGivenAndIsNotRequired() {
		// setup
		when(conditionSearchWidget.getValue(context, request)).thenReturn("");
		
		// replay
		element.handleSubmission(session, request);
		
		// verify
		ArgumentCaptor<Condition> captor = ArgumentCaptor.forClass(Condition.class);
		verify(conditionService, times(0)).saveCondition(captor.capture());
	}
	
	@Test
	public void handleSubmission_shouldNotCreateConditionInViewMode() {
		// setup
		when(context.getMode()).thenReturn(Mode.VIEW);
		
		// replay
		element.handleSubmission(session, request);
		
		// verify
		verify(conditionService, never()).saveCondition(any(Condition.class));
	}
	
	@Test
	public void handleSubmission_shouldSupportFormField() {
		// setup
		element.setControlId("my_condition_tag");
		when(conditionSearchWidget.getValue(context, request)).thenReturn("1519");
		when(conditionStatusesWidget.getValue(context, request)).thenReturn("active");
		
		// Mock session
		Form form = new Form();
		form.setName("MyForm");
		form.setVersion("1.0");
		when(session.getForm()).thenReturn(form);
		doCallRealMethod().when(session).generateControlFormPath(anyString(), anyInt());
		
		// replay
		element.handleSubmission(session, request);
		
		// verify
		Set<Condition> conditions = encounter.getConditions();
		Assert.assertEquals(1, conditions.size());
		
		Condition condition = conditions.iterator().next();
		Assert.assertEquals(ConditionClinicalStatus.ACTIVE, condition.getClinicalStatus());
		Assert.assertThat(condition.getCondition().getCoded().getId(), is(1519));
		Assert.assertEquals("HtmlFormEntry^MyForm.1.0/my_condition_tag-0", condition.getFormNamespaceAndPath());
	}
	
	@Test
	public void validateSubmission_shouldFailValidationWhenConditionIsNotGivenAndIsRequired() {
		// setup
		element.setRequired(true);
		when(conditionSearchWidget.getValue(context, request)).thenReturn(null);
		when(messageSourceService.getMessage("htmlformentry.conditionui.condition.required"))
		        .thenReturn("A condition is required");
		
		// replay
		List<FormSubmissionError> errors = (List<FormSubmissionError>) element.validateSubmission(context, request);
		
		// verify
		Assert.assertEquals("A condition is required", errors.get(0).getError());
	}
	
	@Test
	public void validateSubmission_shouldFailValidationWhenOnsetDateIsGreaterThanEnddate() {
		// setup
		when(endDateWidget.getValue(context, request))
		        .thenReturn(new GregorianCalendar(2012, Calendar.DECEMBER, 8).getTime());
		when(messageSourceService.getMessage("htmlformentry.conditionui.endDate.before.onsetDate.error"))
		        .thenReturn("The end date cannot be ealier than the onset date.");
		
		// replay
		List<FormSubmissionError> errors = (List<FormSubmissionError>) element.validateSubmission(context, request);
		
		// verify
		Assert.assertEquals("The end date cannot be ealier than the onset date.", errors.get(0).getError());
	}
	
	@Test
	public void htmlForConditionSearchWidget_shouldGetConceptSourceClassesFromGP() {
		// setup
		element.setMessageSourceService(messageSourceService);
		when(adminService.getGlobalProperty(ConditionElement.GLOBAL_PROPERTY_CONDITIONS_CRITERIA))
		        .thenReturn("Diagnosis,Finding");
		
		// replay
		String html = element.htmlForConditionSearchWidget(context);
		
		// verify
		Assert.assertTrue(html.contains("setupAutocomplete(this, 'conceptSearch.form','null','Diagnosis,Finding','null')"));
	}
	
	@Test
	public void htmlForConditionSearchWidget_shouldUseDiagnosisConceptClassAsDefaultConceptSource() {
		// setup
		element.setMessageSourceService(messageSourceService);
		when(adminService.getGlobalProperty(ConditionElement.GLOBAL_PROPERTY_CONDITIONS_CRITERIA)).thenReturn(null);
		
		// replay
		String html = element.htmlForConditionSearchWidget(context);
		
		// verify
		Assert.assertTrue(html.contains("setupAutocomplete(this, 'conceptSearch.form','null','Diagnosis','null')"));
		
	}
	
	@Test(expected = IllegalStateException.class)
	public void generateHtml_shouldShouldThrowWhenFormPathIsMissing() {
		// setup
		when(conditionSearchWidget.getValue(context, request)).thenReturn("1519");
		when(conditionStatusesWidget.getValue(context, request)).thenReturn("active");
		when(context.getMode()).thenReturn(Mode.VIEW);
		
		// Mock context
		Encounter encounter = new Encounter();
		Condition condition = new Condition();
		encounter.addCondition(condition);
		when(context.getExistingEncounter()).thenReturn(encounter);
		when(context.getMode()).thenReturn(Mode.VIEW);
		
		// Mock session
		Form form = new Form();
		form.setName("MyForm");
		form.setVersion("1.0");
		when(session.getForm()).thenReturn(form);
		
		// replay
		element.generateHtml(context);
	}
}
