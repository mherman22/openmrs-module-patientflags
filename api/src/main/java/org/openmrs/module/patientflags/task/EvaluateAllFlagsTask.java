package org.openmrs.module.patientflags.task;

import org.openmrs.api.context.Context;
import org.openmrs.module.patientflags.api.FlagService;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Re-evaluates every flag on a schedule.
 *
 * Flags are otherwise only evaluated when a definition is saved or when the AOP advice fires on a
 * clinical write, which covers any criterion that turns on a data change. A criterion that becomes
 * true because time has passed, such as a medication overdue for its interval or a patient not seen
 * for six months, becomes true on a day when nothing is written to that patient's record, so it
 * would never be raised until somebody happened to touch the patient for an unrelated reason.
 */
public class EvaluateAllFlagsTask extends AbstractTask {
	
	private static final Logger log = LoggerFactory.getLogger(EvaluateAllFlagsTask.class);
	
	@Override
	public void execute() {
		// The scheduler runs tasks as the user named in the scheduler.username global property.
		// Without that session there is no privilege to read patients, and a partial evaluation
		// would clear flags it could not re-derive.
		if (!Context.isAuthenticated()) {
			log.warn("Skipping flag evaluation: the scheduler session is not authenticated");
			return;
		}
		
		log.debug("Re-evaluating all patient flags");
		Context.getService(FlagService.class).evaluateAllFlags();
	}
}
