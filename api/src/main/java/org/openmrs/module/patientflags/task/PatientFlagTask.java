/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.patientflags.task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.openmrs.CohortMembership;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.patientflags.Flag;
import org.openmrs.module.patientflags.PatientFlag;
import org.openmrs.module.patientflags.api.FlagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PatientFlagTask implements Runnable {

	private static final Logger log = LoggerFactory.getLogger(PatientFlagTask.class);
	
	private static DaemonToken daemonToken;
	
	private Patient patient;
	
	private Flag flag;
	
	@Override
	public void run() {
		FlagService flagService = Context.getService(FlagService.class);
		
		if (patient != null) {
			log.debug("Generating patient flags for patient {}", patient.getUuid());
			generatePatientFlags(patient, flagService);
		}
		else if (flag != null) {
			log.debug("Generating patient flags for flag '{}'", flag.getName());
			generatePatientFlags(flag, flagService);
		}
		else {
			log.debug("Evaluating all patient flags");
			evaluateAllFlags();
		}
	}
	
	public static Runnable evaluateAllFlags() {
		return Daemon.runInDaemonThread(new AllFlagsEvaluator(), daemonToken);
	}

	public static void setDaemonToken(DaemonToken token) {
		daemonToken = token;
	}
	
	public void generatePatientFlags(Patient patient) {
		this.patient = patient;
		
		if (daemonToken != null) {
			Daemon.runInDaemonThread(this, daemonToken);
		}
	}
	
	public void generatePatientFlags(Flag flag) {
		this.flag = flag;
		
		if (daemonToken != null) {
			Daemon.runInDaemonThread(this, daemonToken);
		}
	}

	private static void generatePatientFlags(Flag flag, FlagService service) {
		generatePatientFlagsForFlagAndPatient(flag,service);
	}

	static void generatePatientFlagsForFlagAndPatient(Flag flag, FlagService service){
		if (!flag.getEnabled() || flag.getRetired()) {
			service.deletePatientFlagsForFlag(flag);
			return;
		}
		
		HashMap<Object, Object> context = new HashMap<Object, Object>();
		org.openmrs.Cohort cohort = service.getFlaggedPatients(flag, context);
		if (cohort == null) {
			return;
		}

		Set<Integer> members = cohort.getMemberships()
				.stream()
				.map(CohortMembership::getPatientId)
				.collect(Collectors.toSet());

		// Write only the difference. Deleting every row and rebuilding would reset date_created on
		// rows whose patient never stopped matching, and that is the only record of how long a flag
		// has been raised.
		Set<Integer> alreadyHeld = new HashSet<Integer>();
		for (PatientFlag held : service.getPatientFlagsForFlag(flag)) {
			Integer patientId = held.getPatient() == null ? null : held.getPatient().getPatientId();
			if (patientId == null) {
				continue;
			}
			if (members.contains(patientId)) {
				alreadyHeld.add(patientId);
			}
			else {
				service.deletePatientFlagForPatient(held.getPatient(), flag);
			}
		}

		for (Integer patientId : members) {
			if (alreadyHeld.contains(patientId)) {
				continue;
			}

			@SuppressWarnings("unchecked")
			List<String> flgs = (List<String>)context.get(patientId);
			if (flgs != null) {
				for (String flg : flgs) {
					service.savePatientFlag(new PatientFlag(new Patient(patientId), flag, flg));
				}
			}
			else {
				service.savePatientFlag(new PatientFlag(new Patient(patientId), flag, flag.evalMessage(patientId)));
			}
		}
	}
	
	void generatePatientFlags(Patient patient, FlagService service) {
		HashMap<Object, Object> context = new HashMap<Object, Object>();
		List<Flag> flags = service.generateFlagsForPatient(patient, context);

		// Same reasoning as the per-flag path: clear only the flags this patient no longer matches
		// and add only the ones that are new, so an unchanged row keeps its date_created.
		Set<Integer> stillMatching = new HashSet<Integer>();
		for (Flag flag : flags) {
			stillMatching.add(flag.getFlagId());
		}
		Set<Integer> alreadyHeld = new HashSet<Integer>();
		for (PatientFlag held : service.getPatientFlags(patient)) {
			Integer flagId = held.getFlag() == null ? null : held.getFlag().getFlagId();
			if (flagId == null) {
				continue;
			}
			if (stillMatching.contains(flagId)) {
				alreadyHeld.add(flagId);
			}
			else {
				service.deletePatientFlagForPatient(patient, held.getFlag());
			}
		}

		for (Flag flag : flags) {
			if (alreadyHeld.contains(flag.getFlagId())) {
				continue;
			}

			@SuppressWarnings("unchecked")
			List<String> flgs = (List<String>)context.get(patient.getPatientId());
			if (flgs != null) {
				for (String flg : flgs) {
					service.savePatientFlag(new PatientFlag(patient, flag, flg));
				}
			}
			else {
				service.savePatientFlag(new PatientFlag(patient, flag, flag.evalMessage(patient.getPatientId())));
			}
		}
	}
	
	//The only reason why we have this class is to be able to run in
	//a daemon thread in order to get daemon access to the database
	private static class AllFlagsEvaluator implements Runnable {

		@Override
		public void run() {
			FlagService flagService = Context.getService(FlagService.class);

			flagService.getAllFlags().forEach(flag -> Daemon.runInNewDaemonThread(new PatientFlagGenerator(flag)));
		}
	}

	private static class PatientFlagGenerator implements  Runnable {
		private final Flag flag;

		PatientFlagGenerator(Flag flag){
			this.flag = flag;
		}

        @Override
        public void run() {
            FlagService service = Context.getService(FlagService.class);
            generatePatientFlagsForFlagAndPatient(flag, service);
        }
    }
}
