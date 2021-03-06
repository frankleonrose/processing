/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013 The Processing Foundation
  Copyright (c) 2011-12 Ben Fry and Casey Reas

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License version 2
  as published by the Free Software Foundation.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License along 
  with this program; if not, write to the Free Software Foundation, Inc.
  59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package processing.app.contrib;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import processing.app.Base;
import processing.core.PApplet;


public class ContributionListing {
  static final String LISTING_URL = 
    "https://raw.github.com/processing/processing-web/master/contrib_generate/contributions.txt";

  static ContributionListing singleInstance;
  
  File listingFile;
  ArrayList<ContributionChangeListener> listeners;
  ArrayList<AvailableContribution> advertisedContributions;
  Map<String, List<Contribution>> librariesByCategory;
  ArrayList<Contribution> allContributions;
  boolean hasDownloadedLatestList;
  ReentrantLock downloadingListingLock;

  static final String[] validCategories = {
    "3D", "Animation", "Compilations", "Data", "Geometry", "GUI", 
    "Hardware", "I/O", "Math", "Simulation", "Sound", "Typography",
    "Utilities", "Video & Vision" 
  };


  private ContributionListing() {
    listeners = new ArrayList<ContributionChangeListener>();
    advertisedContributions = new ArrayList<AvailableContribution>();
    librariesByCategory = new HashMap<String, List<Contribution>>();
    allContributions = new ArrayList<Contribution>();
    downloadingListingLock = new ReentrantLock();

    listingFile = Base.getSettingsFile("contributions.txt");
    listingFile.setWritable(true);
    if (listingFile.exists()) {
      setAdvertisedList(listingFile);
    }
  }


  static ContributionListing getInstance() {
    if (singleInstance == null) {
      singleInstance = new ContributionListing();
    }
    return singleInstance;
  }


  void setAdvertisedList(File file) {
    listingFile = file;

    advertisedContributions.clear();
    advertisedContributions.addAll(parseContribList(listingFile));
    for (Contribution contribution : advertisedContributions) {
      addContribution(contribution);
    }
    Collections.sort(allContributions, nameComparator);
  }

  
  /**
   * Adds the installed libraries to the listing of libraries, replacing any
   * pre-existing libraries by the same name as one in the list.
   */
  protected void updateInstalledList(List<Contribution> installedContributions) {
    for (Contribution contribution : installedContributions) {
      Contribution preexistingContribution = getContribution(contribution);
      if (preexistingContribution != null) {
        replaceContribution(preexistingContribution, contribution);
      } else {
        addContribution(contribution);
      }
    }
  }


  protected void replaceContribution(Contribution oldLib, Contribution newLib) {
    if (oldLib == null || newLib == null) {
      return;
    }

    if (librariesByCategory.containsKey(oldLib.getCategory())) {
      List<Contribution> list = librariesByCategory.get(oldLib.getCategory());

      for (int i = 0; i < list.size(); i++) {
        if (list.get(i) == oldLib) {
          list.set(i, newLib);
        }
      }
    }

    for (int i = 0; i < allContributions.size(); i++) {
      if (allContributions.get(i) == oldLib) {
        allContributions.set(i, newLib);
      }
    }

    notifyChange(oldLib, newLib);
  }


  private void addContribution(Contribution contribution) {
    if (librariesByCategory.containsKey(contribution.getCategory())) {
      List<Contribution> list = librariesByCategory.get(contribution.getCategory());
      list.add(contribution);
      Collections.sort(list, nameComparator);
      
    } else {
      ArrayList<Contribution> list = new ArrayList<Contribution>();
      list.add(contribution);
      librariesByCategory.put(contribution.getCategory(), list);
    }
    allContributions.add(contribution);
    notifyAdd(contribution);
    Collections.sort(allContributions, nameComparator);
  }


  protected void removeContribution(Contribution info) {
    if (librariesByCategory.containsKey(info.getCategory())) {
      librariesByCategory.get(info.getCategory()).remove(info);
    }
    allContributions.remove(info);
    notifyRemove(info);
  }


  private Contribution getContribution(Contribution contribution) {
    for (Contribution c : allContributions) {
      if (c.getName().equals(contribution.getName()) && 
          c.getType() == contribution.getType()) {
        return c;
      }
    }
    return null;
  }


  protected AvailableContribution getAvailableContribution(Contribution info) {
    for (AvailableContribution advertised : advertisedContributions) {
      if (advertised.getType() == info.getType() && 
          advertised.getName().equals(info.getName())) {
        return advertised;
      }
    }
    return null;
  }

  
  protected Set<String> getCategories(ContributionFilter filter) {
    Set<String> outgoing = new HashSet<String>();

    Set<String> categorySet = librariesByCategory.keySet();
    for (String categoryName : categorySet) {
      for (Contribution contrib : librariesByCategory.get(categoryName)) {
        if (filter.matches(contrib)) {
          // TODO still not sure why category would be coming back null [fry]
          // http://code.google.com/p/processing/issues/detail?id=1387
          if (categoryName != null && categoryName.trim().length() != 0) {
            outgoing.add(categoryName);
          }
          break;
        }
      }
    }
    return outgoing;
  }

  
//  public List<Contribution> getAllContributions() {
//    return new ArrayList<Contribution>(allContributions);
//  }

  
//  public List<Contribution> getLibararies(String category) {
//    ArrayList<Contribution> libinfos =
//        new ArrayList<Contribution>(librariesByCategory.get(category));
//    Collections.sort(libinfos, nameComparator);
//    return libinfos;
//  }

  
  protected List<Contribution> getFilteredLibraryList(String category, List<String> filters) {
    ArrayList<Contribution> filteredList = new ArrayList<Contribution>(allContributions);

    Iterator<Contribution> it = filteredList.iterator();
    while (it.hasNext()) {
      Contribution libInfo = it.next();
      if (category != null && !category.equals(libInfo.getCategory())) {
        it.remove();
      } else {
        for (String filter : filters) {
          if (!matches(libInfo, filter)) {
            it.remove();
            break;
          }
        }
      }
    }
    return filteredList;
  }


  private boolean matches(Contribution contrib, String filter) {
    int colon = filter.indexOf(":");
    if (colon != -1) {
      String isText = filter.substring(0, colon);
      String property = filter.substring(colon + 1);

      // Chances are the person is still typing the property, so rather than
      // make the list flash empty (because nothing contains "is:" or "has:",
      // just return true.
      if (!isProperty(property)) {
        return true;
      }

      if ("is".equals(isText) || "has".equals(isText)) {
        return hasProperty(contrib, filter.substring(colon + 1));
      } else if ("not".equals(isText)) {
        return !hasProperty(contrib, filter.substring(colon + 1));
      }
    }

    filter = ".*" + filter.toLowerCase() + ".*";

    return contrib.getAuthorList() != null && contrib.getAuthorList().toLowerCase().matches(filter)
        || contrib.getSentence() != null && contrib.getSentence().toLowerCase().matches(filter)
        || contrib.getParagraph() != null && contrib.getParagraph().toLowerCase().matches(filter)
        || contrib.getCategory() != null && contrib.getCategory().toLowerCase().matches(filter)
        || contrib.getName() != null && contrib.getName().toLowerCase().matches(filter);
  }


  private boolean isProperty(String property) {
    return property.startsWith("updat") || property.startsWith("upgrad")
        || property.startsWith("instal") && !property.startsWith("installabl")
        || property.equals("tool") || property.startsWith("lib")
        || property.equals("mode") || property.equals("compilation");
  }


  /** 
   * Returns true if the contribution fits the given property, false otherwise.
   * If the property is invalid, returns false. 
   */
  private boolean hasProperty(Contribution contrib, String property) {
    // update, updates, updatable, upgrade
    if (property.startsWith("updat") || property.startsWith("upgrad")) {
      return hasUpdates(contrib);
    }
    if (property.startsWith("instal") && !property.startsWith("installabl")) {
      return contrib.isInstalled();
    }
    if (property.equals("tool")) {
      return contrib.getType() == ContributionType.TOOL;
    }
    if (property.startsWith("lib")) {
      return contrib.getType() == ContributionType.LIBRARY;
//      return contrib.getType() == Contribution.Type.LIBRARY
//          || contrib.getType() == Contribution.Type.LIBRARY_COMPILATION;
    }
    if (property.equals("mode")) {
      return contrib.getType() == ContributionType.MODE;
    }
//    if (property.equals("compilation")) {
//      return contrib.getType() == Contribution.Type.LIBRARY_COMPILATION;
//    }

    return false;
  }


  private void notifyRemove(Contribution contribution) {
    for (ContributionChangeListener listener : listeners) {
      listener.contributionRemoved(contribution);
    }
  }


  private void notifyAdd(Contribution contribution) {
    for (ContributionChangeListener listener : listeners) {
      listener.contributionAdded(contribution);
    }
  }


  private void notifyChange(Contribution oldLib, Contribution newLib) {
    for (ContributionChangeListener listener : listeners) {
      listener.contributionChanged(oldLib, newLib);
    }
  }

  
  protected void addContributionListener(ContributionChangeListener listener) {
    for (Contribution contrib : allContributions) {
      listener.contributionAdded(contrib);
    }
    listeners.add(listener);
  }

  
  /*
  private void removeContributionListener(ContributionChangeListener listener) {
    listeners.remove(listener);
  }

  
  private ArrayList<ContributionChangeListener> getContributionListeners() {
    return new ArrayList<ContributionChangeListener>(listeners);
  }
  */

  
  /**
   * Starts a new thread to download the advertised list of contributions. 
   * Only one instance will run at a time.
   */
  protected void downloadAvailableList(final ProgressMonitor progress) {
    new Thread(new Runnable() {
      public void run() {
        downloadingListingLock.lock();

        URL url = null;
        try {
          url = new URL(LISTING_URL);
        } catch (MalformedURLException e) {
          progress.error(e);
          progress.finished();
        }

        if (!progress.isFinished()) {
          ContributionManager.download(url, listingFile, progress);
          if (!progress.isCanceled() && !progress.isError()) {
            hasDownloadedLatestList = true;
            setAdvertisedList(listingFile);
          }
        }
        downloadingListingLock.unlock();
      }
    }).start();
  }
  
  
  boolean hasUpdates() {
    for (Contribution info : allContributions) {
      if (hasUpdates(info)) {
        return true;
      }
    }
    return false;
  }


  boolean hasUpdates(Contribution contribution) {
    if (contribution.isInstalled()) {
      Contribution advertised = getAvailableContribution(contribution);
      if (advertised == null) {
        return false;
      }
      return advertised.getVersion() > contribution.getVersion();
    }
    return false;
  }

  
  boolean hasDownloadedLatestList() {
    return hasDownloadedLatestList;
  }

  
  /**
   * @return a lowercase string with all non-alphabetic characters removed
   */
  static protected String normalize(String s) {
    return s.toLowerCase().replaceAll("^\\p{Lower}", "");
  }

  
  /**
   * @return the proper, valid name of this category to be displayed in the UI
   *         (e.g. "Typography / Geometry"). "Unknown" if the category null.
   */
  static public String getCategory(String category) {
    if (category == null) {
      return "Unknown";
    }
    String normCatName = normalize(category);

    for (String validCatName : validCategories) {
      String normValidCatName = normalize(validCatName);
      if (normValidCatName.equals(normCatName)) {
        return validCatName;
      }
    }
    return category;
  }

  
  ArrayList<AvailableContribution> parseContribList(File file) {
    ArrayList<AvailableContribution> outgoing = new ArrayList<AvailableContribution>();

    if (file != null && file.exists()) {
      String lines[] = PApplet.loadStrings(file);

      int start = 0;
      while (start < lines.length) {
//        // Only consider 'invalid' lines. These lines contain the type of
//        // software: library, tool, mode
//        if (!lines[start].contains("=")) {
        String type = lines[start];
        ContributionType contribType = ContributionType.fromName(type);
        if (contribType == null) {
          System.err.println("Error in contribution listing file on line " + (start+1));
          return outgoing;
        }

        // Scan forward for the next blank line
        int end = ++start;
        while (end < lines.length && !lines[end].equals("")) {
          end++;
        }

        int length = end - start;
        String[] contribLines = new String[length];
        System.arraycopy(lines, start, contribLines, 0, length);

        HashMap<String,String> contribParams = new HashMap<String,String>();
        Base.readSettings(file.getName(), contribLines, contribParams);
        
        outgoing.add(new AvailableContribution(contribType, contribParams));
        start = end + 1;
//        } else {
//          start++;
//        }
      }
    }
    return outgoing;
  }

  
//  boolean isDownloadingListing() {
//    return downloadingListingLock.isLocked();
//  }

  
  public Comparator<? super Contribution> getComparator() {
    return nameComparator;
  }

  
  static Comparator<Contribution> nameComparator = new Comparator<Contribution>() {
    public int compare(Contribution o1, Contribution o2) {
      return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
    }
  };
}
