/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.google.gson.Gson;
import com.intellij.codeInsight.completion.*;

import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import static com.intellij.patterns.PlatformPatterns.psiElement;

import com.intellij.psi.*;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


/**
 * The `BeamCompletionContributor` class is a subclass of `CompletionContributor` that provides code completion
 * suggestions specifically for Apache Beam pipelines.
 */
public class BeamCompletionContributor extends CompletionContributor {
    public static final String[] beamJavaSDKTransforms = {
        "Filter", "FlatMapElements", "Keys", "KvSwap", "MapElements", "ParDo",
                "Partition", "Regex", "Reify", "ToString", "WithKeys", "WithTimestamps",
                "Values", "ApproximateQuantiles", "ApproximateUnique", "CoGroupByKey", "Combine",
                "CombineWithContext", "Count", "Distinct", "GroupByKey", "GroupIntoBatches",
                "HllCount", "Latest", "Max", "Mean", "Min", "Sample", "Sum", "Top",
                "Create", "Flatten", "PAssert", "View", "Window"
    };

    /**
     * A pattern condition that matches method call expressions with the name "apply" in the context of the
     * Apache Beam `Pipeline` class.
     */
    public static final PatternCondition<PsiMethodCallExpression> APPLY_METHOD_PATTERN = new PatternCondition<>("") {
        @Override
        public boolean accepts(@NotNull PsiMethodCallExpression psiMethodCallExpression, ProcessingContext context) {
            String referenceName = psiMethodCallExpression.getMethodExpression().getReferenceName();
            if (referenceName != null){
                if (!referenceName.equals("apply")) {
                    return false;
                }
                PsiMethod resolvedMethod = psiMethodCallExpression.resolveMethod();
                if (resolvedMethod != null){
                    PsiClass containingClass = resolvedMethod.getContainingClass();
                    if (containingClass != null){
                        return "org.apache.beam.sdk.Pipeline".equals(containingClass.getQualifiedName()) ||
                                "org.apache.beam.sdk.values.PCollection".equals(containingClass.getQualifiedName());
                    }
                }
            }
            return false;
        }
    };

    /**
     * A pattern that matches an identifier after a method call expression that satisfies the `APPLY_METHOD_PATTERN`.
     */
    private static final PsiJavaElementPattern.Capture<PsiIdentifier> AFTER_METHOD_CALL_PATTERN =
            PsiJavaPatterns.psiElement(PsiIdentifier.class)
                    .withParent(
                            psiElement(PsiReferenceExpression.class)
                                    .withParent(
                                            psiElement(PsiExpressionList.class)
                                                    .withParent(
                                                            psiElement(PsiMethodCallExpression.class)
                                                                    .with(APPLY_METHOD_PATTERN)
                                                    )
                                    )
                    );

    /**
     * Fills the completion variants with Transforms from Java SDK.
     */
    @Override
    public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull CompletionResultSet result) {
        if (AFTER_METHOD_CALL_PATTERN.accepts(parameters.getPosition())){
            try {
                int offset = parameters.getOffset();
                String full_code = parameters.getOriginalFile().getText();
                String code_to_complete = full_code.substring(0, offset);

                Parameters model_parameters = new Parameters(0.1F, false);
                RequestBody req_body_obj = new RequestBody(code_to_complete, model_parameters);

                String request_body_json = new Gson().toJson(req_body_obj);
                String model_url = "https://api-inference.huggingface.co/models/bigcode/starcoder";
                String hf_api_key = System.getenv("HF_API_KEY");


                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(model_url))
                        .headers("Content-Type", "application/json", "Authorization", "Bearer " + hf_api_key)
                        .POST(HttpRequest.BodyPublishers.ofString(request_body_json))
                        .build();

                HttpClient httpClient = HttpClient.newHttpClient();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                String body = response.body();
                // todo: integrate the completions to IDE UI (inline)
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
