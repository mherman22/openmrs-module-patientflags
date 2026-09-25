package org.openmrs.module.patientflags.task;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientflags.Flag;
import org.openmrs.module.patientflags.api.FlagService;
import org.openmrs.test.BaseModuleContextSensitiveTest;

public class EvaluateAllFlagsTaskTest extends BaseModuleContextSensitiveTest {
	
	protected static final String XML_DATASET_PATH = "org/openmrs/module/patientflags/include/";
	
	private static final String TEST_DATASET_FILE = XML_DATASET_PATH + "patientflagtest-dataset.xml";
	
	private FlagService flagService;
	
	@Before
	public void initTestData() throws Exception {
		initializeInMemoryDatabase();
		executeDataSet(TEST_DATASET_FILE);
		flagService = Context.getService(FlagService.class);
		authenticate();
	}
	
	/**
	 * The task exists so that a criterion which becomes true with the passage of time is picked up
	 * without anyone writing to the patient's record, so it has to raise a flag for a patient who
	 * matches but holds no row.
	 */
	@Test
	public void execute_shouldRaiseAFlagForAPatientThatHasNoRowYet() {
		Flag flag = flagService.getFlag(1);
		flagService.deletePatientFlagsForFlag(flag);
		assertTrue(flagService.getPatientFlagsForFlag(flag).isEmpty());
		
		PatientFlagTask.generatePatientFlagsForFlagAndPatient(flag, flagService);
		
		assertTrue(!flagService.getPatientFlagsForFlag(flag).isEmpty());
	}
	
	@Test
	public void execute_shouldDoNothingWhenTheSessionIsNotAuthenticated() {
		Context.logout();
		new EvaluateAllFlagsTask().execute();
		// Reaching here without an APIException is the assertion: an unauthenticated run must
		// return rather than clear flags it has no privilege to re-derive.
		Context.authenticate("admin", "test");
		assertEquals(1, flagService.getFlag(1).getFlagId().intValue());
	}
}
