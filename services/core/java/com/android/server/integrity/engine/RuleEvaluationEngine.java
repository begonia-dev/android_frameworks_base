/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.integrity.engine;

import android.content.integrity.AppInstallMetadata;
import android.content.integrity.AtomicFormula;
import android.content.integrity.CompoundFormula;
import android.content.integrity.IntegrityFormula;
import android.content.integrity.Rule;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.integrity.IntegrityFileManager;
import com.android.server.integrity.model.IntegrityCheckResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The engine used to evaluate rules against app installs.
 *
 * <p>Every app install is evaluated against rules (pushed by the verifier) by the evaluation engine
 * to allow/block that install.
 */
public class RuleEvaluationEngine {
    private static final String TAG = "RuleEvaluation";

    // The engine for loading rules, retrieving metadata for app installs, and evaluating app
    // installs against rules.
    private static RuleEvaluationEngine sRuleEvaluationEngine;

    private final IntegrityFileManager mIntegrityFileManager;

    @VisibleForTesting
    RuleEvaluationEngine(IntegrityFileManager integrityFileManager) {
        mIntegrityFileManager = integrityFileManager;
    }

    /** Provide a singleton instance of the rule evaluation engine. */
    public static synchronized RuleEvaluationEngine getRuleEvaluationEngine() {
        if (sRuleEvaluationEngine == null) {
            return new RuleEvaluationEngine(IntegrityFileManager.getInstance());
        }
        return sRuleEvaluationEngine;
    }

    /**
     * Load, and match the list of rules against an app install metadata.
     *
     * @param appInstallMetadata Metadata of the app to be installed, and to evaluate the rules
     *                           against.
     * @return result of the integrity check
     */
    public IntegrityCheckResult evaluate(
            AppInstallMetadata appInstallMetadata, Map<String, String> allowedInstallers) {
        List<Rule> rules = loadRules(appInstallMetadata);
        allowedInstallersRule(allowedInstallers).ifPresent(rules::add);
        return RuleEvaluator.evaluateRules(rules, appInstallMetadata);
    }

    private List<Rule> loadRules(AppInstallMetadata appInstallMetadata) {
        try {
            return mIntegrityFileManager.readRules(appInstallMetadata);
        } catch (Exception e) {
            Slog.e(TAG, "Error loading rules.", e);
            return new ArrayList<>();
        }
    }

    private static Optional<Rule> allowedInstallersRule(Map<String, String> allowedInstallers) {
        if (allowedInstallers.isEmpty()) {
            return Optional.empty();
        }

        List<IntegrityFormula> formulas = new ArrayList<>(allowedInstallers.size());
        allowedInstallers.forEach(
                (installer, cert) -> {
                    formulas.add(allowedInstallerFormula(installer, cert));
                });

        // We need this special case since OR-formulas require at least two operands.
        IntegrityFormula allInstallersFormula =
                formulas.size() == 1
                        ? formulas.get(0)
                        : new CompoundFormula(CompoundFormula.OR, formulas);

        return Optional.of(
                new Rule(
                        new CompoundFormula(
                                CompoundFormula.NOT, Arrays.asList(allInstallersFormula)),
                        Rule.DENY));
    }

    private static IntegrityFormula allowedInstallerFormula(String installer, String cert) {
        return new CompoundFormula(
                CompoundFormula.AND,
                Arrays.asList(
                        new AtomicFormula.StringAtomicFormula(
                                AtomicFormula.INSTALLER_NAME,
                                installer,
                                /* isHashedValue= */ false),
                        new AtomicFormula.StringAtomicFormula(
                                AtomicFormula.INSTALLER_CERTIFICATE, cert, /* isHashedValue= */
                                false)));
    }
}
