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

package org.pentaho.di.job.entries.talendjobexec;

import static org.pentaho.di.job.entry.validator.AndValidator.putValidators;
import static org.pentaho.di.job.entry.validator.JobEntryValidatorUtils.andValidator;
import static org.pentaho.di.job.entry.validator.JobEntryValidatorUtils.notBlankValidator;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.vfs.AllFileSelector;
import org.apache.commons.vfs.FileObject;
import org.pentaho.di.cluster.SlaveServer;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.Result;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleDatabaseException;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.fileinput.FileInputList;
import org.pentaho.di.core.plugins.KettleURLClassLoader;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.vfs.KettleVFS;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.job.JobMeta;
import org.pentaho.di.job.entry.JobEntryBase;
import org.pentaho.di.job.entry.JobEntryInterface;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.resource.ResourceDefinition;
import org.pentaho.di.resource.ResourceEntry;
import org.pentaho.di.resource.ResourceEntry.ResourceType;
import org.pentaho.di.resource.ResourceNamingInterface;
import org.pentaho.di.resource.ResourceReference;
import org.w3c.dom.Node;

/**
 * This executes an exported Talend Job.
 * 
 * @author Matt
 * @since 1-04-2011
 * 
 */
public class JobEntryTalendJobExec extends JobEntryBase implements Cloneable, JobEntryInterface {
  private static Class<?> PKG = JobEntryTalendJobExec.class; // for i18n
                                                             
  private static Map<String,ClassLoader> classLoaderCache = new HashMap<String, ClassLoader>();

  private String          filename;
  private String          className;

  public JobEntryTalendJobExec(String n) {
    super(n, ""); //$NON-NLS-1$
    setID(-1L);

    filename = null;
  }

  public JobEntryTalendJobExec() {
    this(""); //$NON-NLS-1$
  }

  public Object clone() {
    JobEntryTalendJobExec je = (JobEntryTalendJobExec) super.clone();
    return je;
  }

  public String getXML() {
    StringBuffer retval = new StringBuffer();

    retval.append(super.getXML());
    retval.append("      ").append(XMLHandler.addTagValue("filename", filename)); //$NON-NLS-1$ //$NON-NLS-2$
    retval.append("      ").append(XMLHandler.addTagValue("class_name", className)); //$NON-NLS-1$ //$NON-NLS-2$

    return retval.toString();
  }

  public void loadXML(Node entrynode, List<DatabaseMeta> databases, List<SlaveServer> slaveServers, Repository rep) throws KettleXMLException {
    try {
      super.loadXML(entrynode, databases, slaveServers);
      filename = XMLHandler.getTagValue(entrynode, "filename"); //$NON-NLS-1$
      className = XMLHandler.getTagValue(entrynode, "class_name"); //$NON-NLS-1$
    } catch (KettleXMLException xe) {
      throw new KettleXMLException(BaseMessages.getString(PKG, "JobEntryTalendJobExec.ERROR_0001_Cannot_Load_Job_Entry_From_Xml_Node"), xe); //$NON-NLS-1$
    }
  }

  public void loadRep(Repository rep, ObjectId id_jobentry, List<DatabaseMeta> databases, List<SlaveServer> slaveServers) throws KettleException {
    try {
      filename = rep.getJobEntryAttributeString(id_jobentry, "filename"); //$NON-NLS-1$
      className = rep.getJobEntryAttributeString(id_jobentry, "class_name"); //$NON-NLS-1$
    } catch (KettleException dbe) {
      throw new KettleException(BaseMessages.getString(PKG, "JobEntryTalendJobExec.ERROR_0002_Cannot_Load_Job_From_Repository", id_jobentry), dbe); //$NON-NLS-1$
    }
  }

  public void saveRep(Repository rep, ObjectId id_job) throws KettleException {
    try {
      rep.saveJobEntryAttribute(id_job, getObjectId(), "filename", filename); //$NON-NLS-1$
      rep.saveJobEntryAttribute(id_job, getObjectId(), "class_name", className); //$NON-NLS-1$
    } catch (KettleDatabaseException dbe) {
      throw new KettleException(BaseMessages.getString(PKG, "JobEntryTalendJobExec.ERROR_0003_Cannot_Save_Job_Entry", id_job), dbe); //$NON-NLS-1$
    }
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getFilename() {
    return filename;
  }

  public String getRealFilename() {
    return environmentSubstitute(getFilename());
  }

  public Result execute(Result previousResult, int nr) {
    Result result = previousResult;
    result.setResult(false);

    if (filename != null) {
      String realFilename = getRealFilename();
      try {
        FileObject file = KettleVFS.getFileObject(realFilename, this);
        if (file.exists() && file.isReadable()) {
          result = executeTalenJob(file, result, nr);
        } else {
          logDetailed(BaseMessages.getString(PKG, "JobEntryTalendJobExec.File_Does_Not_Exist", realFilename)); //$NON-NLS-1$
        }
      } catch (Exception e) {
        result.setNrErrors(1);
        logError(BaseMessages.getString(PKG, "JobEntryTalendJobExec.ERROR_0004_IO_Exception", e.getMessage()), e); //$NON-NLS-1$
      }
    } else {
      result.setNrErrors(1);
      logError(BaseMessages.getString(PKG, "JobEntryTalendJobExec.ERROR_0005_No_Filename_Defined")); //$NON-NLS-1$
    }

    return result;
  }

  private Result executeTalenJob(FileObject file, Result result, int nr) throws Exception {

    ClassLoader classLoader = null;
    try {

      classLoader = classLoaderCache.get(file.toString());
      if (classLoader==null) {
        // Find the jar files in the ZIP file...
        //
        final URL[] jarFiles = prepareJarFiles(file);

        // Create a new class loader with the extracted jar files.
        //
        classLoader = new KettleURLClassLoader(jarFiles, getClass().getClassLoader());

        Runtime.getRuntime().addShutdownHook(new Thread() {
          
          @Override
          public void run() {
            try {
              cleanupJarFiles(jarFiles);
            } catch(Exception e) {
              System.err.println("Error cleaning up temporary Talend jar file extracts: "+Const.getStackTracker(e));
            }
          }
        });
        
        classLoaderCache.put(file.toString(), classLoader);
      }

      Class<?> clazz = classLoader.loadClass(environmentSubstitute(getClassName()));
      Object jobObject = clazz.newInstance();
      Method runJob = clazz.getMethod("runJobInTOS", String[].class);

      // TODO: consider passing something of some sort in this next method:
      // variables, arguments...
      //
      int returnCode = (Integer) runJob.invoke(jobObject, (Object) new String[] {});

      result.setExitStatus(returnCode);
      result.setResult(true);
      result.setNrErrors(0);

    } catch (Exception e) {
      throw new KettleException(BaseMessages.getString(PKG, "JobEntryTalendJobExec.ERROR_0006_ExceptionExecutingTalenJob"), e);
    }

    return result;
  }

  private URL[] prepareJarFiles(FileObject zipFile) throws Exception {

    FileInputList fileList = FileInputList.createFileList(this, new String[] { "zip:" + zipFile.toString(), }, // zip:file:///tmp/foo.zip
        new String[] { ".*\\.jar$", }, // Include mask: only jar files
        new String[] { ".*classpath\\.jar$", }, // Exclude mask: only jar files
        new String[] { "Y", }, // File required
        new boolean[] { true, } // Search sub-directories
        );

    List<URL> files = new ArrayList<URL>();

    // Copy the jar files in the temp folder...
    //
    for (FileObject file : fileList.getFiles()) {
      FileObject jarfilecopy = KettleVFS.createTempFile(file.getName().getBaseName(), ".jar", environmentSubstitute("${java.io.tmpdir}"));
      jarfilecopy.copyFrom(file, new AllFileSelector());
      files.add(jarfilecopy.getURL());
    }

    return files.toArray(new URL[files.size()]);
  }

  private void cleanupJarFiles(URL[] jarFiles) throws Exception {
    if (jarFiles == null)
      return;

    for (URL jarFile : jarFiles) {
      File file = new File(jarFile.toURI());
      file.delete();
    }
  }

  public boolean evaluates() {
    return true;
  }

  public List<ResourceReference> getResourceDependencies(JobMeta jobMeta) {
    List<ResourceReference> references = super.getResourceDependencies(jobMeta);
    if (!Const.isEmpty(filename)) {
      String realFileName = jobMeta.environmentSubstitute(filename);
      ResourceReference reference = new ResourceReference(this);
      reference.getEntries().add(new ResourceEntry(realFileName, ResourceType.FILE));
      references.add(reference);
    }
    return references;
  }

  @Override
  public void check(List<CheckResultInterface> remarks, JobMeta jobMeta) {
    andValidator().validate(this, "filename", remarks, putValidators(notBlankValidator())); //$NON-NLS-1$
  }

  /**
   * Since the exported job that runs this will reside in a ZIP file, we can't
   * reference files relatively. So what this does is turn the name of files
   * into absolute paths OR it simply includes the resource in the ZIP file. For
   * now, we'll simply turn it into an absolute path and pray that the file is
   * on a shared drive or something like that. TODO: create options to configure
   * this behavior
   */
  public String exportResources(VariableSpace space, Map<String, ResourceDefinition> definitions, ResourceNamingInterface resourceNamingInterface, Repository repository) throws KettleException {
    try {
      // The object that we're modifying here is a copy of the original!
      // So let's change the filename from relative to absolute by grabbing the
      // file object...
      // In case the name of the file comes from previous steps, forget about
      // this!
      //
      if (!Const.isEmpty(filename)) {
        // From : ${FOLDER}/../foo/bar.csv
        // To : /home/matt/test/files/foo/bar.csv
        //
        FileObject fileObject = KettleVFS.getFileObject(space.environmentSubstitute(filename), space);

        // If the file doesn't exist, forget about this effort too!
        //
        if (fileObject.exists()) {
          // Convert to an absolute path...
          //
          filename = resourceNamingInterface.nameResource(fileObject, space, true);

          return filename;
        }
      }
      return null;
    } catch (Exception e) {
      throw new KettleException(e); //$NON-NLS-1$
    }
  }

  /**
   * @return the className
   */
  public String getClassName() {
    return className;
  }

  /**
   * @param className
   *          the className to set
   */
  public void setClassName(String className) {
    this.className = className;
  }
}
