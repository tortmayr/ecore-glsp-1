/********************************************************************************
 * Copyright (c) 2019-2020 EclipseSource and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0, or the MIT License which is
 * available at https://opensource.org/licenses/MIT.
 *
 * SPDX-License-Identifier: EPL-2.0 OR MIT
 ********************************************************************************/
package org.eclipse.emfcloud.ecore.glsp.operationhandler;

import static org.eclipse.glsp.api.jsonrpc.GLSPServerException.getOrThrow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.EStructuralFeature.Setting;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.EcoreUtil.UsageCrossReferencer;
import org.eclipse.emfcloud.ecore.enotation.NotationElement;
import org.eclipse.emfcloud.ecore.enotation.Shape;
import org.eclipse.emfcloud.ecore.glsp.EcoreFacade;
import org.eclipse.emfcloud.ecore.glsp.EcoreModelIndex;
import org.eclipse.emfcloud.ecore.glsp.ResourceManager;
import org.eclipse.emfcloud.ecore.glsp.model.EcoreModelState;
import org.eclipse.emfcloud.ecore.glsp.util.EcoreConfig.Types;
import org.eclipse.emfcloud.ecore.glsp.util.EcoreEdgeUtil;
import org.eclipse.glsp.api.jsonrpc.GLSPServerException;
import org.eclipse.glsp.api.model.GraphicalModelState;
import org.eclipse.glsp.api.operation.kind.ApplyLabelEditOperation;
import org.eclipse.glsp.graph.GModelElement;
import org.eclipse.glsp.graph.GNode;
import org.eclipse.glsp.server.operationhandler.BasicOperationHandler;

public class EcoreLabelEditOperationHandler extends BasicOperationHandler<ApplyLabelEditOperation> {

	@Override
	public void executeOperation(ApplyLabelEditOperation editLabelOperation, GraphicalModelState graphicalModelState) {
		EcoreFacade facade = EcoreModelState.getEcoreFacade(graphicalModelState);
		EcoreModelIndex index = EcoreModelState.getModelState(graphicalModelState).getIndex();
		Optional<String> type = index.findElementByClass(editLabelOperation.getLabelId(), GModelElement.class).map(e -> e.getType());
		if (type.isPresent()) {
			switch (type.get()) { 
				case Types.LABEL_NAME:
						GNode node = getOrThrow(index.findElementByClass(editLabelOperation.getLabelId(), GNode.class), 
							"No parent Node for element with id " + editLabelOperation.getLabelId() + " found");
						
						EObject node_semantic = getOrThrow(index.getSemantic(node),
							"No semantic element for labelContainer with id " + node.getId() + " found");
		
						Shape shape = getOrThrow(index.getNotation(node_semantic), Shape.class,
								"No shape element for label with id " + editLabelOperation.getLabelId() + " found");
		
						if (node_semantic instanceof EClassifier) {
							String oldRootURI = EcoreUtil.getURI(node_semantic).fragment().toString().substring(2);
							((EClassifier) node_semantic).setName(editLabelOperation.getText().trim());
							// nameChange== uri change so we have to recreate the proxy here
							// remove the old uri from the uri map
							index.removeURI(shape.getSemanticElement().getUri());
							shape.setSemanticElement(facade.createProxy(node_semantic));
							// Rename all the uris for references from this element
							String newRootURI = shape.getSemanticElement().getUri().toString().substring(2);
							index.updateReferenceRoot(oldRootURI, newRootURI);
						}
					break;
				case Types.LABEL_INSTANCE:
					node = getOrThrow(index.findElementByClass(editLabelOperation.getLabelId(), GNode.class), 
							"No parent Node for element with id " + editLabelOperation.getLabelId() + " found");

					node_semantic = getOrThrow(index.getSemantic(node),
							"No semantic element for labelContainer with id " + node.getId() + " found");
					if (node_semantic instanceof EClassifier) {
						((EClassifier) node_semantic).setInstanceClassName(editLabelOperation.getText().trim());
					}
					break;
				case Types.ATTRIBUTE:
					EAttribute attribute_semantic = (EAttribute) getOrThrow(index.getSemantic(editLabelOperation.getLabelId()),
						"No semantic element for label with id " + editLabelOperation.getLabelId() + " found");

					String inputText = editLabelOperation.getText();
					String attributeName;
					if (inputText.contains(":")) {
						String[] split = inputText.split(":");
						attributeName = split[0].trim();
		
						Optional<EClassifier> datatype = parseStringToEType(split[1].trim(),
								EcoreModelState.getResourceManager(graphicalModelState));
						if (datatype.isPresent()) {
							attribute_semantic.setEType(datatype.get());
						}
					} else {
						attributeName = inputText.trim();
					}
					index.removeURI(EcoreUtil.getURI(attribute_semantic).fragment().toString());
					if (!inputText.isEmpty()) {
						attribute_semantic.setName(attributeName);
					}
					break;

				case Types.ENUMLITERAL:
					EEnumLiteral literal_semantic = (EEnumLiteral) getOrThrow(index.getSemantic(editLabelOperation.getLabelId()),
						"No semantic element for label with id " + editLabelOperation.getLabelId() + " found");
					index.removeURI(EcoreUtil.getURI(literal_semantic).fragment().toString());
					String text = editLabelOperation.getText().trim();
					if (!text.isEmpty()) {
						literal_semantic.setName(text);
					}
					break;

				case Types.LABEL_EDGE_NAME:
					String edgeId = EcoreEdgeUtil.getEdgeId(editLabelOperation.getLabelId());
					EReference reference_semantic = (EReference) getOrThrow(
						index.getSemantic(edgeId),
						"No semantic element for labelContainer with id " + edgeId + " found");
					String oldRootURI = EcoreUtil.getURI(reference_semantic.getEContainingClass()).fragment() + "/" + reference_semantic.getName();
					reference_semantic.setName(editLabelOperation.getText().trim());
					/*Optional<NotationElement> reference_notation = index.getNotation(reference_semantic);
					String newURI = "//" + reference_semantic.getEContainingClass().getName()  + "/" + editLabelOperation.getText().trim();
					index.updateURI(reference_notation.get().getSemanticElement().getUri(), newURI);
					reference_notation.get().getSemanticElement().setUri(newURI);*/
					index.updateReferenceLabel(oldRootURI, editLabelOperation.getText().trim());
					break;

				case Types.LABEL_EDGE_MULTIPLICITY:
					edgeId = EcoreEdgeUtil.getEdgeId(editLabelOperation.getLabelId());
					reference_semantic = (EReference) getOrThrow(
						index.getSemantic(edgeId),
						"No semantic element for labelContainer with id " + edgeId + " found");
					Pattern pattern = Pattern.compile("\\s*\\[\\s*(\\d+)\\s*\\.+\\s*(\\*|\\d+|\\-1)\\s*\\]\\s*");
						Matcher matcher = pattern.matcher(editLabelOperation.getText());
						if (matcher.matches()) {
							String lowerBound = matcher.group(1);
							String upperBound = matcher.group(2);
							reference_semantic.setLowerBound((lowerBound.equals("*")) ? -1 : Integer.valueOf(lowerBound));
							reference_semantic.setUpperBound((upperBound.equals("*")) ? -1 : Integer.valueOf(upperBound));
						} else {
							throw new GLSPServerException("Multiplicity of reference with id " + editLabelOperation.getLabelId() + " has a wrong input format", new IllegalArgumentException());
						}
					break;
			}
		}
	}

	private Optional<EClassifier> parseStringToEType(String name, ResourceManager resManager) {
		for (EClassifier type : getAllEAttributeTypes(resManager)) {
			if (type.getName().toLowerCase().equals(name.toLowerCase())) {
				return Optional.ofNullable(type);
			}
		}
		return Optional.empty();
	}

	public static List<EClassifier> getAllEAttributeTypes(ResourceManager resManager) {
		List<EClassifier> listOfTypes = new ArrayList<>(EcorePackage.eINSTANCE.getEClassifiers());
		listOfTypes.removeIf(e -> !(e instanceof EDataType));
		TreeIterator<Notifier> resourceSetContent = resManager.getEditingDomain().getResourceSet().getAllContents();
		while (resourceSetContent.hasNext()) {
			Notifier res = resourceSetContent.next();
			if (res instanceof EDataType) {
				listOfTypes.add((EClassifier) res);
			}
		}
		return listOfTypes;
	}

	@Override
	public String getLabel() {
		return "Apply label";
	}
}
