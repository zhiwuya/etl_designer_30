/*******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2012 by Pentaho : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.di.imp.rules;

import java.util.ArrayList;
import java.util.List;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.imp.rule.ImportValidationFeedback;
import org.pentaho.di.imp.rule.ImportValidationResultType;
import org.pentaho.di.imp.rule.ImportRuleInterface;
import org.pentaho.di.trans.TransHopMeta;
import org.pentaho.di.trans.TransMeta;
import org.w3c.dom.Node;

public class TransformationHasNoDisabledHopsImportRule extends BaseImportRule implements ImportRuleInterface {

  public TransformationHasNoDisabledHopsImportRule() {
    super();
  }
  
  @Override
  public List<ImportValidationFeedback> verifyRule(Object subject) {
    
    List<ImportValidationFeedback> feedback = new ArrayList<ImportValidationFeedback>();
    
    if (!isEnabled()) return feedback;
    if (!(subject instanceof TransMeta)) return feedback;
    
    TransMeta transMeta = (TransMeta)subject;

    for (int i=0;i<transMeta.nrTransHops();i++) {
      TransHopMeta hop = transMeta.getTransHop(i);
      if (!hop.isEnabled()) {
        feedback.add( new ImportValidationFeedback(this, ImportValidationResultType.ERROR, "There is a disabled hop in the transformation.") );
      }
    }

    if (feedback.isEmpty()) {
      feedback.add( new ImportValidationFeedback(this, ImportValidationResultType.APPROVAL, "All hops are enabled in this transformation.") );
    }
    
    return feedback;
  }

  @Override
  public String getXML() {
    
    StringBuilder xml = new StringBuilder();
    xml.append(XMLHandler.openTag(XML_TAG));

    xml.append(super.getXML()); // id, enabled
        
    xml.append(XMLHandler.closeTag(XML_TAG));
    return xml.toString();
  }

  @Override
  public void loadXML(Node ruleNode) throws KettleException {
    super.loadXML(ruleNode);
  }

}
