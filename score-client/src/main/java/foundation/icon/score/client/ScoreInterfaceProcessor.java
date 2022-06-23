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

import com.squareup.javapoet.*;
import foundation.icon.annotation_processor.AbstractProcessor;
import foundation.icon.annotation_processor.ProcessorUtil;
import score.Address;
import score.Context;
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

/*
 * TODO generate exception each Method
 * GenericPredefinedException
 * - Status.OutOfStep
 * - Status.StackOverflow
 * handle IllegalArgumentException
 * - Status.ContractNotFound
 * - Status.MethodNotFound
 * - Status.MethodNotPayable
 * - Status.InvalidParameter
 * - Status.OutOfBalance
 * - Status.PackageError
 * handle RevertedException
 * - Status.UnknownFailure
 * - s < Status.UserReversionStart
 * handle UserRevertedException
 * - Status.UserReversionStart <= s < Status.UserReversionEnd
 *
 * <InterfaceName>Exception extends score.UserRevertException
 * <InterfaceNameMethodName>Exception extends <InterfaceName>Exception
 */
public class ScoreInterfaceProcessor extends AbstractProcessor {
    static final String MEMBER_ADDRESS = "address";
    static final String PARAM_PAYABLE_VALUE = "valueForPayable";

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> s = new HashSet<>();
        s.add(ScoreInterface.class.getCanonicalName());
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
                if (element.getKind().isInterface() || element.getKind().isClass()) {
                    messager.noteMessage("process %s %s", element.getKind(), element.asType(), element.getSimpleName());
                    generateImplementClass(processingEnv.getFiler(), (TypeElement) element);
                    ret = true;
                } else {
                    throw new RuntimeException("not support");
                }
            }
        }
        return ret;
    }

    private void generateImplementClass(Filer filer, TypeElement element) {
        ClassName interfaceClassName = ClassName.get(element);
        TypeSpec typeSpec = typeSpec(element);
        JavaFile javaFile = JavaFile.builder(interfaceClassName.packageName(), typeSpec).build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private TypeSpec typeSpec(TypeElement element) {
        ClassName interfaceClassName = ClassName.get(element);
        ScoreInterface scoreInterface = element.getAnnotation(ScoreInterface.class);
        ClassName className = ClassName.get(interfaceClassName.packageName(), interfaceClassName.simpleName() + scoreInterface.suffix());
        TypeSpec.Builder builder = TypeSpec
                .classBuilder(ClassName.get(interfaceClassName.packageName(), className.simpleName()))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        if (element.getKind().isInterface()) {
            builder.addSuperinterface(element.asType());
        }

        //Fields
        builder.addField(Address.class, MEMBER_ADDRESS, Modifier.PROTECTED, Modifier.FINAL);

        //Constructor
        builder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(Address.class, MEMBER_ADDRESS).build())
                .addStatement("this.$L = $L", MEMBER_ADDRESS, MEMBER_ADDRESS).build());

        //addressGetter
        builder.addMethod(MethodSpec.methodBuilder(scoreInterface.addressGetter())
                .addModifiers(Modifier.PUBLIC)
                .returns(Address.class)
                .addStatement("return this.$L", MEMBER_ADDRESS).build());

        List<MethodSpec> methods = overrideMethods(element);
        builder.addMethods(methods);
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
                    MethodSpec methodSpec = methodSpec(ee, mustGenerate);
                    addMethod(methods, methodSpec, element);
                    if (external != null && !external.readonly() && ee.getAnnotation(Payable.class) != null) {
                        addMethod(methods, payableMethodSpec(ee, methodSpec), element);
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
                messager.warningMessage(
                        "Redeclare '%s %s(%s)' in %s",
                        conflictMethod.returnType.toString(),
                        conflictMethod.name,
                        ProcessorUtil.parameterSpecToString(conflictMethod.parameters),
                        element.getQualifiedName());
            }
            methods.add(methodSpec);
        }
    }

    private String callParameters(ExecutableElement element) {
        StringJoiner variables = new StringJoiner(", ");
        variables.add(String.format("this.%s", MEMBER_ADDRESS));
        variables.add(String.format("\"%s\"", element.getSimpleName().toString()));
        for (VariableElement variableElement : element.getParameters()) {
            variables.add(variableElement.getSimpleName().toString());
        }
        return variables.toString();
    }

    private MethodSpec methodSpec(ExecutableElement ee, boolean mustGenerate) {
        if (ee.getAnnotation(EventLog.class) != null) {
            return notSupportedMethod(ee, "not supported EventLog method");
        }

        String methodName = ee.getSimpleName().toString();
        TypeMirror returnType = ee.getReturnType();
        TypeName returnTypeName = TypeName.get(returnType);

        MethodSpec.Builder builder = MethodSpec
                .methodBuilder(methodName)
//                .addAnnotation(Override.class)
                .addModifiers(ProcessorUtil.getModifiers(ee, Modifier.ABSTRACT))
                .addParameters(ProcessorUtil.getParameterSpecs(ee))
                .returns(returnTypeName);

        String callParameters = callParameters(ee);
        if (returnTypeName.equals(TypeName.VOID)) {
            builder.addStatement("$T.call($L)", Context.class, callParameters);
        } else {
            if (returnType.getKind().equals(TypeKind.DECLARED) &&
                    ((DeclaredType)returnType).getTypeArguments().size() > 0) {
                builder.addStatement("return ($T)$T.call($L)", returnTypeName, Context.class, callParameters);
            } else {
                builder.addStatement("return $T.call($T.class, $L)", Context.class, returnTypeName, callParameters);
            }
        }
        return builder.build();
    }

    private MethodSpec payableMethodSpec(ExecutableElement ee, MethodSpec methodSpec) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(methodSpec.name)
                .addModifiers(methodSpec.modifiers)
                .addParameter(BigInteger.class, PARAM_PAYABLE_VALUE)
                .addParameters(methodSpec.parameters)
                .returns(methodSpec.returnType);

        String callParameters = callParameters(ee);
        TypeMirror returnType = ee.getReturnType();
        if (methodSpec.returnType.equals(TypeName.VOID)) {
            builder.addStatement("$T.call($L, $L)", Context.class, PARAM_PAYABLE_VALUE, callParameters);
        } else {
            if (returnType.getKind().equals(TypeKind.DECLARED) &&
                    ((DeclaredType)returnType).getTypeArguments().size() > 0) {
                builder.addStatement("return ($T)$T.call($L, $L)", methodSpec.returnType, Context.class, PARAM_PAYABLE_VALUE, callParameters);
            } else {
                builder.addStatement("return $T.call($T.class, $L, $L)", Context.class, methodSpec.returnType, PARAM_PAYABLE_VALUE, callParameters);
            }
        }
        return builder.build();
    }

    private MethodSpec notSupportedMethod(ExecutableElement ee, String msg) {
        String methodName = ee.getSimpleName().toString();
        TypeName returnTypeName = TypeName.get(ee.getReturnType());
        return MethodSpec.methodBuilder(methodName)
                .addModifiers(ProcessorUtil.getModifiers(ee, Modifier.ABSTRACT))
                .addParameters(ProcessorUtil.getParameterSpecs(ee))
                .returns(returnTypeName)
                .addStatement("throw new $T(\"$L\")", RuntimeException.class, msg)
                .addJavadoc("@deprecated Do not use this method, this is generated only for preventing compile error. $L\n", msg)
                .addJavadoc("@throws $L", RuntimeException.class.getName())
                .addAnnotation(Deprecated.class)
                .build();
    }
}
