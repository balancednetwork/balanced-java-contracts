/*
 * Copyright (c) 2022-2022 Balanced.network.
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

package network.balanced.score.xcall.annotation;

import com.squareup.javapoet.*;
import foundation.icon.annotation_processor.AbstractProcessor;
import network.balanced.score.xcall.util.ArgumentParser;
import network.balanced.score.xcall.util.ArgumentSerializer;
import network.balanced.score.xcall.util.XCallMessage;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.*;
import score.Context;

public class XCallProcessor extends AbstractProcessor {
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> s = new HashSet<>();
        s.add(XCall.class.getCanonicalName());
        return s;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotationElements = roundEnv.getElementsAnnotatedWith(annotation);
            if (annotationElements.isEmpty()) {
                return false ;
            }

            generateProcessorClass(processingEnv.getFiler(), annotationElements);
            generateMessageClass(processingEnv.getFiler(), annotationElements);

            return true;
        }

        return false;
    }

    private void generateProcessorClass(Filer filer, Set<? extends Element> elements) {
        Element element =  elements.iterator().next();
        TypeElement typeElement = (TypeElement) element.getEnclosingElement();
        ClassName elementClassName = ClassName.get(typeElement);

        XCall ann = element.getAnnotation(XCall.class);
        ClassName className = ClassName.get(elementClassName.packageName(), elementClassName.simpleName() + ann.suffix());

        TypeSpec typeSpec = processorTypeSpec(elementClassName, className, elements);
        JavaFile javaFile = JavaFile.builder(className.packageName(), typeSpec).build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            messager.warningMessage("create javaFile error : %s", e.getMessage());
        }
    }

    private void generateMessageClass(Filer filer, Set<? extends Element> elements) {
        Element element =  elements.iterator().next();
        TypeElement typeElement = (TypeElement) element.getEnclosingElement();
        ClassName elementClassName = ClassName.get(typeElement);

        String messagesPackage  = this.getClass().getPackageName().replaceFirst("annotation", "messages");
        ClassName className = ClassName.get(messagesPackage, elementClassName.simpleName() + "Messages");

        TypeSpec typeSpec = messagesTypeSpec(className, elements);
        JavaFile javaFile = JavaFile.builder(className.packageName(), typeSpec).build();
        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            messager.warningMessage("create javaFile error : %s", e.getMessage());
        }
    }

    private TypeSpec processorTypeSpec(ClassName elementClassName, ClassName className, Set<? extends Element> elements) {
        TypeSpec.Builder builder = TypeSpec
                .classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        MethodSpec.Builder handleMethod = MethodSpec.methodBuilder("process")
            .addModifiers(Modifier.PUBLIC)
            .addModifiers(Modifier.STATIC)
            .addParameter(elementClassName, "score")
            .addParameter(ParameterSpec.builder(String.class, "from").build())
            .addParameter(ParameterSpec.builder(byte[].class, "data").build())
            .addStatement("$T args = $T.parse(new String(data)).asArray()", JsonArray.class, Json.class)
            .addStatement("String method = args.get(0).asString()")
            .beginControlFlow("switch (method)");

        for (Element element : elements) {
            Name methodName = element.getSimpleName();
            ExecutableElement executableElement = (ExecutableElement) element;

            List<? extends VariableElement> parameters = executableElement.getParameters();
            VariableElement fromParam = parameters.get(0);
            if(!fromParam.toString().equals("from") || !fromParam.asType().toString().endsWith(".String")) {
                throw new RuntimeException("First parameter in a XCall must be the from parameter, (String from)");
            }

            handleMethod.addCode("case $S: \n", methodName);
            handleMethod.addCode("$> score." + methodName + "(from");

            for (int i = 1; i < parameters.size(); i++) {
                TypeMirror type = parameters.get(i).asType();
                String parser = getParseMethod(type.toString());
                handleMethod.addCode(", $T." + parser + ".apply(args.get(" + i +"))", ArgumentParser.class);
            }

            handleMethod.addCode(");\n$<");
            handleMethod.addStatement("$>break$<");

        }

        handleMethod.addCode("default: \n");
        handleMethod.addStatement("$>$T.revert()$<", Context.class);
        handleMethod.endControlFlow();
        builder.addMethod(handleMethod.build());

        return builder.build();
    }

    private TypeSpec messagesTypeSpec(ClassName className, Set<? extends Element> elements) {
        TypeSpec.Builder builder = TypeSpec
                .classBuilder(className)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        for (Element element : elements) {
            Name methodName = element.getSimpleName();
            MethodSpec.Builder createMethod = MethodSpec.methodBuilder(methodName.toString())
                .addModifiers(Modifier.PUBLIC)
                .addModifiers(Modifier.STATIC)
                .returns(XCallMessage.class)
                .addStatement("$T msg = new $T()", XCallMessage.class, XCallMessage.class);

            ExecutableElement executableElement = (ExecutableElement) element;
            List<? extends VariableElement> parameters = executableElement.getParameters();
            for (int i = 1; i < parameters.size(); i++) {
                TypeName type = TypeName.get(parameters.get(i).asType());
                Name name = parameters.get(i).getSimpleName();
                String serializer = getSerializeMethod(type.toString());

                createMethod.addParameter(type, name.toString());
                createMethod.addStatement("msg.data.add($T.$L.apply($L))", ArgumentSerializer.class, serializer, name.toString());
            }

            createMethod.addStatement("return msg");
            builder.addMethod(createMethod.build());
        }

        return builder.build();
    }

    private String getParseMethod(String paramType) {
        paramType = paramType.substring(paramType.lastIndexOf(".") + 1);
        switch(paramType) {
            case "String":
                return "parseString";
            case "String[]":
                return "parseStringArray";
            case "Address":
                return "parseAddress";
            case "Address[]":
                return "parseAddressArray";
            case "BigInteger":
                return "parseBigInteger";
            case "BigInteger[]":
                return "parseBigIntegerArray";
            case "Boolean":
                return "parseBoolean";
            case "Boolean[]":
                return "parseBooleanArray";
            case "Map<String, Object>":
                return "parseStruct";
            case "Map<String, Object>[]":
                return " parseMapArray";
            case "byte[]":
                return "parseBytes";
            default:
                throw new RuntimeException("XCall annotations does not support parameter type " + paramType);
        }
    }

    private String getSerializeMethod(String paramType) {
        paramType = paramType.substring(paramType.lastIndexOf(".") + 1);
        switch(paramType) {
            case "String":
                return "serializeString";
            case "String[]":
                return "serializeStringArray";
            case "Address":
                return "serializeAddress";
            case "Address[]":
                return "serializeAddressArray";
            case "BigInteger":
                return "serializeBigInteger";
            case "BigInteger[]":
                return "serializeBigIntegerArray";
            case "Boolean":
                return "serializeBoolean";
            case "Boolean[]":
                return "serializeBooleanArray";
            case "byte[]":
                return "serializeBytes";
            default:
                throw new RuntimeException("XCall annotations does not support parameter type " + paramType);
        }
    }
}