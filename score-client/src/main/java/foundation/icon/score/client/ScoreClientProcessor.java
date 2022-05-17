/*
 * Copyright 2021 ICON Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package foundation.icon.score.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.squareup.javapoet.*;
import foundation.icon.annotation_processor.AbstractProcessor;
import foundation.icon.annotation_processor.ProcessorUtil;
import foundation.icon.icx.Wallet;
import foundation.icon.jsonrpc.Address;
import foundation.icon.jsonrpc.model.TransactionResult;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Payable;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;

public class ScoreClientProcessor extends AbstractProcessor {
    static final String METHOD_OF = "_of";
    static final String PARAM_PROPERTEIS = "properties";
    static final String PARAM_PREFIX = "prefix";
    static final String METHOD_DEPLOY = "_deploy";
    static final String PARAM_URL = "url";
    static final String PARAM_NID = "nid";
    static final String PARAM_WALLET = "wallet";
    static final String PARAM_ADDRESS = "address";
    static final String PARAM_CLIENT = "client";
    static final String PARAM_SCORE_FILE_PATH = "scoreFilePath";
    static final String PARAM_PARAMS = "params";
    //
    static final String PARAM_PAYABLE_VALUE = "valueForPayable";
    static final String PARAM_CONSUMER = "consumerFunc";
    static final String PARAM_STEP_LIMIT = "stepLimit";

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> s = new HashSet<>();
        s.add(ScoreClient.class.getCanonicalName());
        return s;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        boolean ret = false;
        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotationElements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : annotationElements) {
                if (element.getKind().isInterface() || element.getKind().isClass() || element.getKind().isField()) {
                    messager.noteMessage("process %s %s", element.getKind(), element.asType(), element.getSimpleName());
                    generateImplementClass(processingEnv.getFiler(), element);
                    ret = true;
                } else {
                    throw new RuntimeException("not support");
                }
            }
        }
        return ret;
    }

    private void generateImplementClass(Filer filer, Element element) {
        TypeElement typeElement;
        if (element instanceof TypeElement) {
            typeElement = (TypeElement) element;
        } else if (element instanceof VariableElement) {
            typeElement = super.getTypeElement(element.asType());
        } else {
            throw new RuntimeException("not support");
        }

        ClassName elementClassName = ClassName.get(typeElement);
        ScoreClient ann = element.getAnnotation(ScoreClient.class);
        ClassName className = ClassName.get(elementClassName.packageName(), elementClassName.simpleName() + ann.suffix());
        TypeSpec typeSpec = typeSpec(className, typeElement);
        JavaFile javaFile = JavaFile.builder(className.packageName(), typeSpec).build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            messager.warningMessage("create javaFile error : %s", e.getMessage());
        }
    }

    private TypeSpec typeSpec(ClassName className, TypeElement element) {
        TypeSpec.Builder builder = TypeSpec
                .classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(DefaultScoreClient.class)
                .addSuperinterfaces(ProcessorUtil.getSuperinterfaces(element));

        if (element.getKind().isInterface()) {
            builder.addSuperinterface(element.asType());
            builder.addMethod(deployMethodSpec(className, null));
        }

        //Constructor
        builder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(String.class, PARAM_URL).build())
                .addParameter(ParameterSpec.builder(BigInteger.class, PARAM_NID).build())
                .addParameter(ParameterSpec.builder(Wallet.class, PARAM_WALLET).build())
                .addParameter(ParameterSpec.builder(Address.class, PARAM_ADDRESS).build())
                .addStatement("super($L, $L, $L, $L)",
                        PARAM_URL, PARAM_NID, PARAM_WALLET, PARAM_ADDRESS).build());
        builder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(DefaultScoreClient.class, PARAM_CLIENT).build())
                .addStatement("super($L)", PARAM_CLIENT).build());

        //_of(Properties)
        builder.addMethod(MethodSpec.methodBuilder(METHOD_OF)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ParameterSpec.builder(Properties.class, PARAM_PROPERTEIS).build())
                .addStatement("return _of(\"\", $L)", PARAM_PROPERTEIS)
                .returns(className)
                .build());
        //_of(String prefix, Properties)
        builder.addMethod(MethodSpec.methodBuilder(METHOD_OF)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ParameterSpec.builder(String.class, PARAM_PREFIX).build())
                .addParameter(ParameterSpec.builder(Properties.class, PARAM_PROPERTEIS).build())
                .addStatement("return new $T($T.of($L, $L))", className, DefaultScoreClient.class, PARAM_PREFIX, PARAM_PROPERTEIS)
                .returns(className)
                .build());

        builder.addMethods(overrideMethods(element));
        builder.addMethods(deployMethods(className, element));
        return builder.build();
    }

    private List<MethodSpec> overrideMethods(TypeElement element) {
        List<MethodSpec> methods = new ArrayList<>();
        TypeMirror superClass = element.getSuperclass();
        if (!superClass.getKind().equals(TypeKind.NONE) && !superClass.toString().equals(Object.class.getName())) {
            messager.noteMessage("superClass[kind:%s, name:%s]", superClass.getKind().name(), superClass.toString());
            List<MethodSpec> superMethods = overrideMethods(super.getTypeElement(element.getSuperclass()));
            methods.addAll(superMethods);
        }

        for (TypeMirror inf : element.getInterfaces()) {
            TypeElement infElement = super.getTypeElement(inf);
            List<MethodSpec> infMethods = overrideMethods(infElement);
            methods.addAll(infMethods);
        }

        boolean mustGenerate = element.getKind().isInterface();
        for (Element enclosedElement : element.getEnclosedElements()) {
            if (ElementKind.METHOD.equals(enclosedElement.getKind()) &&
                    ProcessorUtil.hasModifier(enclosedElement, Modifier.PUBLIC) &&
                    !ProcessorUtil.hasModifier(enclosedElement, Modifier.STATIC)) {
                ExecutableElement ee = (ExecutableElement) enclosedElement;
                External external = ee.getAnnotation(External.class);

                if (external != null || mustGenerate) {
                    CodeBlock paramsCodeblock = paramsCodeblock(ee);
                    MethodSpec methodSpec = methodSpec(ee, paramsCodeblock);
                    addMethod(methods, methodSpec, element);
                    boolean isExternal = external != null ?
                            !external.readonly() :
                            methodSpec.returnType.equals(TypeName.VOID);
                    if (isExternal) {
                        addMethod(methods, consumerMethodSpec(methodSpec, paramsCodeblock, false), element);
                        if (ee.getAnnotation(Payable.class) != null) {
                            addMethod(methods, payableMethodSpec(methodSpec, paramsCodeblock), element);
                            addMethod(methods, consumerMethodSpec(methodSpec, paramsCodeblock, true), element);
                        }
                    }
                }
            }
        }
        return methods;
    }

    private void addMethod(List<MethodSpec> methods, MethodSpec methodSpec, TypeElement element) {
        if (methodSpec != null) {
            MethodSpec conflictMethod = ProcessorUtil.getConflictMethod(methods, methodSpec);
            if (conflictMethod != null) {
                methods.remove(conflictMethod);
                if (element.getKind().isInterface()) {
                    messager.warningMessage(
                            "Redeclare '%s %s(%s)' in %s",
                            conflictMethod.returnType.toString(),
                            conflictMethod.name,
                            ProcessorUtil.parameterSpecToString(conflictMethod.parameters),
                            element.getQualifiedName());
                }
            }
            methods.add(methodSpec);
        }
    }

    private CodeBlock paramsCodeblock(ExecutableElement element) {
        if (element == null || element.getParameters() == null || element.getParameters().size() == 0) {
            return null;
        }
        CodeBlock.Builder builder = CodeBlock.builder();
        builder.addStatement("$T<$T,$T> $L = new $T<>()",
                Map.class, String.class, Object.class, PARAM_PARAMS, HashMap.class);
        for (VariableElement ve : element.getParameters()) {
            ParameterSpec ps = ParameterSpec.get(ve);
            builder.addStatement("$L.put(\"$L\",$L)", PARAM_PARAMS, ps.name, ps.name);
        }
        return builder.build();
    }

    static Map<TypeKind, TypeName> wrapperTypeNames = Map.of(
            TypeKind.BOOLEAN, TypeName.get(Boolean.class),
            TypeKind.BYTE, TypeName.get(Boolean.class),
            TypeKind.SHORT, TypeName.get(Byte.class),
            TypeKind.INT, TypeName.get(Integer.class),
            TypeKind.LONG, TypeName.get(Long.class),
            TypeKind.CHAR, TypeName.get(Character.class),
            TypeKind.FLOAT, TypeName.get(Float.class),
            TypeKind.DOUBLE, TypeName.get(Double.class));

    private MethodSpec methodSpec(ExecutableElement ee, CodeBlock paramsCodeblock) {
        if (ee.getAnnotation(EventLog.class) != null) {
            return notSupportedMethod(ee, "not supported EventLog method", null);
        }

        String methodName = ee.getSimpleName().toString();
        TypeMirror returnType = ee.getReturnType();
        TypeName returnTypeName = TypeName.get(returnType);
        External external = ee.getAnnotation(External.class);

        MethodSpec.Builder builder = MethodSpec
                .methodBuilder(methodName)
                .addModifiers(ProcessorUtil.getModifiers(ee, Modifier.ABSTRACT))
                .addParameters(ProcessorUtil.getParameterSpecs(ee))
                .returns(returnTypeName);
//                .addAnnotation(Override.class);

        String params = "null";
        if (paramsCodeblock != null) {
            builder.addCode(paramsCodeblock);
            params = PARAM_PARAMS;
        }

        boolean isVoid = returnTypeName.equals(TypeName.VOID);
        boolean isExternal = external != null ? !external.readonly() : isVoid;
        if (isExternal) {
            if (isVoid) {
                builder.addStatement("super._send(\"$L\", $L)", methodName, params);
                if (ee.getAnnotation(Payable.class) != null) {
                    builder.addJavadoc("To payable, use $L($T $L, ...)", methodName, BigInteger.class, PARAM_PAYABLE_VALUE);
                }
            } else {
                return notSupportedMethod(ee, "not supported response of writable method in ScoreClient",
                        CodeBlock.builder().add("$L($T<$T> $L, ...)",
                                methodName, Consumer.class, TransactionResult.class, PARAM_CONSUMER).build());
            }
        } else {
            if (!isVoid) {
                if (returnType.getKind().isPrimitive()) {
                    builder.addStatement("return super._call($T.class, \"$L\", $L)",
                            wrapperTypeNames.get(returnType.getKind()), methodName, params);
                } else {
                    if (returnType.getKind().equals(TypeKind.DECLARED) &&
                            ((DeclaredType)returnType).getTypeArguments().size() > 0) {
                        builder.addStatement("return super._call(new $T<$T>(){}, \"$L\", $L)",
                                TypeReference.class, returnTypeName, methodName, params);
                    } else {
                        builder.addStatement("return super._call($T.class, \"$L\", $L)",
                                returnTypeName, methodName, params);
                    }
                }
            } else {
                return notSupportedMethod(ee, "not supported, void of readonly method in ScoreClient", null);
            }
        }
        return builder.build();
    }

    private MethodSpec payableMethodSpec(MethodSpec methodSpec, CodeBlock paramsCodeblock) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(methodSpec.name)
                .addModifiers(methodSpec.modifiers)
                .addParameter(BigInteger.class, PARAM_PAYABLE_VALUE)
                .addParameters(methodSpec.parameters)
                .returns(TypeName.VOID);

        String params = "null";
        if (paramsCodeblock != null) {
            builder.addCode(paramsCodeblock);
            params = PARAM_PARAMS;
        }
        builder.addStatement("super._send($L, \"$L\", $L)", PARAM_PAYABLE_VALUE, methodSpec.name, params);
        return builder.build();
    }

    private MethodSpec consumerMethodSpec(MethodSpec methodSpec, CodeBlock paramsCodeblock, boolean isPayable) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(methodSpec.name)
                .addModifiers(methodSpec.modifiers)
                .addParameter(ParameterSpec.builder(
                        ParameterizedTypeName.get(Consumer.class, TransactionResult.class), PARAM_CONSUMER).build())
                .returns(TypeName.VOID);

        String params = "null";
        if (paramsCodeblock != null) {
            builder.addCode(paramsCodeblock);
            params = PARAM_PARAMS;
        }
        if (isPayable) {
            builder.addParameter(BigInteger.class, PARAM_PAYABLE_VALUE)
                    .addStatement("$L.accept(super._send($L, \"$L\", $L))",
                            PARAM_CONSUMER, PARAM_PAYABLE_VALUE, methodSpec.name, params);
        } else {
            builder.addStatement("$L.accept(super._send(\"$L\", $L))", PARAM_CONSUMER, methodSpec.name, params);
        }
        return builder.addParameters(methodSpec.parameters).build();
    }

    private MethodSpec notSupportedMethod(ExecutableElement ee, String msg, CodeBlock instead) {
        String methodName = ee.getSimpleName().toString();
        TypeName returnTypeName = TypeName.get(ee.getReturnType());
        return MethodSpec.methodBuilder(methodName)
                .addModifiers(ProcessorUtil.getModifiers(ee, Modifier.ABSTRACT))
                .addParameters(ProcessorUtil.getParameterSpecs(ee))
                .returns(returnTypeName)
                .addStatement("throw new $T(\"$L\")", RuntimeException.class, msg)
                .addJavadoc("@deprecated Do not use this method, this is generated only for preventing compile error.\n Instead, use $L\n",
                        instead != null ? instead : "N/A")
                .addJavadoc("@throws $L(\"$L\")", RuntimeException.class.getName(), msg)
                .addAnnotation(Deprecated.class)
                .build();
    }

    private List<MethodSpec> deployMethods(ClassName className, TypeElement element) {
        List<MethodSpec> methods = new ArrayList<>();
        TypeMirror superClass = element.getSuperclass();
        if (!superClass.getKind().equals(TypeKind.NONE) && !superClass.toString().equals(Object.class.getName())) {
            messager.noteMessage("superClass[kind:%s, name:%s]", superClass.getKind().name(), superClass.toString());
            List<MethodSpec> superMethods = deployMethods(className, super.getTypeElement(element.getSuperclass()));
            methods.addAll(superMethods);
        }

        for (Element enclosedElement : element.getEnclosedElements()) {
            if (ElementKind.CONSTRUCTOR.equals(enclosedElement.getKind()) &&
                    ProcessorUtil.hasModifier(enclosedElement, Modifier.PUBLIC)) {
                methods.add(deployMethodSpec(className, (ExecutableElement) enclosedElement));
            }
        }
        return methods;
    }

    private MethodSpec deployMethodSpec(ClassName className, ExecutableElement element) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(METHOD_DEPLOY)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(ParameterSpec.builder(String.class, PARAM_URL).build())
                .addParameter(ParameterSpec.builder(BigInteger.class, PARAM_NID).build())
                .addParameter(ParameterSpec.builder(Wallet.class, PARAM_WALLET).build())
                .addParameter(ParameterSpec.builder(String.class, PARAM_SCORE_FILE_PATH).build())
                .returns(className);

        if (element != null) {
            builder.addParameters(ProcessorUtil.getParameterSpecs(element));
        } else {
            builder.addParameter(ParameterSpec.builder(
                    ParameterizedTypeName.get(Map.class, String.class, Object.class), PARAM_PARAMS).build());
        }

        CodeBlock paramsCodeblock = paramsCodeblock(element);
        if (paramsCodeblock != null) {
            builder.addCode(paramsCodeblock);
        }
        builder
                .addStatement("return new $T($T._deploy($L,$L,$L,$L,$L))",
                        className, DefaultScoreClient.class,
                        PARAM_URL, PARAM_NID, PARAM_WALLET, PARAM_SCORE_FILE_PATH,
                        paramsCodeblock != null || element == null ? PARAM_PARAMS : "null")
                .build();
        return builder.build();
    }

}
