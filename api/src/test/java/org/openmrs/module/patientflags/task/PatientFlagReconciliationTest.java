package org.openmrs.module.patientflags.task;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.patientflags.Flag;
import org.openmrs.module.patientflags.PatientFlag;
import org.openmrs.module.patientflags.api.FlagService;
import org.openmrs.test.BaseModuleContextSensitiveTest;

/**
 * Generation is called here directly rather than through generatePatientFlags(Patient), because
 * that hands the work to a daemon thread whose session the test cannot see, so assertions there
 * pass whatever the generator does.
 */
public class PatientFlagReconciliationTest extends BaseModuleContextSensitiveTest {
	
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
	
	private List<Integer> rowIdsFor(Flag flag) {
		List<Integer> ids = new ArrayList<Integer>();
		for (PatientFlag patientFlag : flagService.getPatientFlagsForFlag(flag)) {
			ids.add(patientFlag.getPatientFlagId());
		}
		Collections.sort(ids);
		return ids;
	}
	
	/**
	 * The point of reconciling. A row whose patient still matches carries the only record of how
	 * long the flag has been raised, and rebuilding the table resets it.
	 */
	@Test
	public void generatePatientFlagsForFlag_shouldLeaveRowsForPatientsThatStillMatch() {
		Flag flag = flagService.getFlag(1);
		
		PatientFlagTask.generatePatientFlagsForFlagAndPatient(flag, flagService);
		List<Integer> firstPass = rowIdsFor(flag);
		assertFalse(firstPass.isEmpty());
		
		PatientFlagTask.generatePatientFlagsForFlagAndPatient(flag, flagService);
		
		assertEquals(firstPass, rowIdsFor(flag));
	}
	
	@Test
	public void generatePatientFlagsForFlag_shouldClearRowsForPatientsThatNoLongerMatch() {
		Flag flag = flagService.getFlag(1);
		Patient patient = Context.getService(PatientService.class).getPatient(2);
		flagService.savePatientFlag(new PatientFlag(patient, flag, "stale"));
		
		// A criterion nobody satisfies, so every row held for this flag is now stale.
		flag.setCriteria("select patient_id from patient where patient_id = -1");
		flagService.saveFlag(flag);
		
		PatientFlagTask.generatePatientFlagsForFlagAndPatient(flag, flagService);
		
		assertTrue(flagService.getPatientFlagsForFlag(flag).isEmpty());
	}
	
	@Test
	public void generatePatientFlagsForFlag_shouldClearEveryRowForADisabledFlag() {
		Flag flag = flagService.getFlag(1);
		PatientFlagTask.generatePatientFlagsForFlagAndPatient(flag, flagService);
		assertFalse(flagService.getPatientFlagsForFlag(flag).isEmpty());
		
		flag.setEnabled(Boolean.FALSE);
		PatientFlagTask.generatePatientFlagsForFlagAndPatient(flag, flagService);
		
		assertTrue(flagService.getPatientFlagsForFlag(flag).isEmpty());
	}
}
