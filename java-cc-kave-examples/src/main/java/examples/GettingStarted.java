/**
 * Copyright 2016 University of Zurich
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package examples;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import cc.kave.commons.model.events.ActivityEvent;
import cc.kave.commons.model.events.CommandEvent;
import cc.kave.commons.model.events.IDEEvent;
import cc.kave.commons.model.events.completionevents.CompletionEvent;
import cc.kave.commons.model.events.testrunevents.TestCaseResult;
import cc.kave.commons.model.events.testrunevents.TestRunEvent;
import cc.kave.commons.model.events.visualstudio.BuildEvent;
import cc.kave.commons.model.events.visualstudio.BuildTarget;
import cc.kave.commons.model.ssts.ISST;
import cc.kave.commons.utils.io.IReadingArchive;
import cc.kave.commons.utils.io.ReadingArchive;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Simple example that shows how the interaction dataset can be opened, all
 * users identified, and all contained events deserialized.
 */
public class GettingStarted {

	private String eventsDir;
	private List<BuildEvent> buildEventList = new ArrayList<>();
	private List<TestRunEvent> testRunEventList = new ArrayList<>();
	private List<ActivityEvent> activityEventList = new ArrayList<>();

	public GettingStarted(String eventsDir) {
		this.eventsDir = eventsDir;
	}

	public void run() throws Exception {

		System.out.printf("looking (recursively) for events in folder %s\n", new File(eventsDir).getAbsolutePath());

		/*
		 * Each .zip that is contained in the eventsDir represents all events that we
		 * have collected for a specific user, the folder represents the first day when
		 * the user uploaded data.
		 */
		Set<String> userZips = IoHelper.findAllZips(eventsDir);

		for (String userZip : userZips) {
			System.out.printf("\n#### processing user zip: %s #####\n", userZip);
//            if(activityEventList.size() > 316927){
//                break;
//            }
			processUserZip(userZip);
		}

        //Write to CSV
        if(!buildEventList.isEmpty()){
            String[] header = {"Active Document","Active Window","TriggeredAt","Duration","Successful"};
            writeBuildEventCSV(buildEventList, "buildEvent", header);
        }

        if(!activityEventList.isEmpty()){
            String[] header = {"TriggeredAt", "TriggeredBy", "Duration"};
            writeActivityEventCSV(activityEventList, "activityEvent", header);
        }

        if(!testRunEventList.isEmpty()){
            String[] header = {"Active Document","Active Window","TriggeredAt","Duration","Result"};
            writeTestRunEventCSV(testRunEventList, "testRunEvent", header);
        }
	}

	private void processUserZip(String userZip) {
		int numProcessedEvents = 0;
		// open the .zip file ...
		try (IReadingArchive ra = new ReadingArchive(new File(eventsDir, userZip))) {
			// ... and iterate over content.
			// the iteration will stop after 200 events to speed things up, remove this
			// guard to process all events.
            // &&  (numProcessedEvents++ < 1000000)

			while (ra.hasNext() ) {
				/*
				 * within the userZip, each stored event is contained as a single file that
				 * contains the Json representation of a subclass of IDEEvent.
				 */
				IDEEvent e = ra.getNext(IDEEvent.class);
				processEvent(e);
			}
		}
	}

	/*
	 * if you review the type hierarchy of IDEEvent, you will realize that several
	 * subclasses exist that provide access to context information that is specific
	 * to the event type.
	 * 
	 * To access the context, you should check for the runtime type of the event and
	 * cast it accordingly.
	 * 
	 * As soon as I have some more time, I will implement the visitor pattern to get
	 * rid of the casting. For now, this is recommended way to access the contents.
	 */
	private void processEvent(IDEEvent e) {

		if (e instanceof CommandEvent) {
			process((CommandEvent) e);
		} else if (e instanceof ActivityEvent) {
		    if(activityEventList.size() < 500000)
			    activityEventList.add((ActivityEvent) e);
		} else if (e instanceof BuildEvent) {
		    if (buildEventList.size() < 14000)
			    buildEventList.add((BuildEvent) e);
        }else if (e instanceof TestRunEvent) {
		    if (testRunEventList.size() < 4000)
			    testRunEventList.add((TestRunEvent) e);
        }else{
			/*
			 * CommandEvent and Completion event are just two examples, please explore the
			 * type hierarchy of IDEEvent to find other types and review their API to
			 * understand what kind of context data is available.
			 * 
			 * We include this "fall back" case, to show which basic information is always
			 * available.
			 */
			processBasic(e);
		}

	}

	private void process(CommandEvent ce) {
		System.out.printf("found a CommandEvent (id: %s)\n", ce.getCommandId());
	}

	private void process(CompletionEvent e) {

		ISST snapshotOfEnclosingType = e.context.getSST();
		String enclosingTypeName = snapshotOfEnclosingType.getEnclosingType().getFullName();

		System.out.printf("found a CompletionEvent (was triggered in: %s)\n", enclosingTypeName);
	}

    private void process(BuildEvent e){
        List<BuildTarget> buildTargetList = e.Targets;
        for (BuildTarget bt : buildTargetList){
            System.out.println(bt.Successful);
        }
    }

    private void process(TestRunEvent e){
        Set<TestCaseResult> testCaseResults = e.Tests;
        for (TestCaseResult tr : testCaseResults){
            System.out.println(tr.Result.toString());
        }
    }


	private void processBasic(IDEEvent e) {
		String eventType = e.getClass().getSimpleName();
		ZonedDateTime triggerTime = e.getTriggeredAt();

		System.out.printf("found an %s that has been triggered at: %s)\n", eventType, triggerTime);
	}

    private static void writeBuildEventCSV(List<BuildEvent> events, String filename, String[] headers) throws Exception{
        FileWriter fileWriter = new FileWriter("C:\\Users\\cyprian\\Desktop\\Project\\" + filename + "_data.csv", true);

        final String NEW_LINE_SEPARATOR = "\n";

        CSVPrinter csvPrinter = new CSVPrinter(fileWriter, CSVFormat.EXCEL.withRecordSeparator(NEW_LINE_SEPARATOR).withHeader(headers));
        for(BuildEvent event : events){
            List<BuildTarget> buildTargetList = event.Targets;
            for (BuildTarget bt : buildTargetList){
                csvPrinter.printRecord(event.ActiveDocument, event.ActiveWindow, event.TriggeredAt,
						event.Duration.toMillis(),bt.Successful);
            }

        }
        csvPrinter.flush();
    }

    private static void writeActivityEventCSV(List<ActivityEvent> events, String filename, String[] headers) throws Exception{
        FileWriter fileWriter = new FileWriter("C:\\Users\\cyprian\\Desktop\\Project\\" + filename + "_data.csv", true);

        final String NEW_LINE_SEPARATOR = "\n";

        CSVPrinter csvPrinter = new CSVPrinter(fileWriter, CSVFormat.EXCEL.withRecordSeparator(NEW_LINE_SEPARATOR).withHeader(headers));
        for(ActivityEvent event : events){
            csvPrinter.printRecord(event.getTriggeredAt(), event.TriggeredBy, event.Duration.toMillis());
        }
        csvPrinter.flush();
    }

//    private static String getReadableTime(Long millisec){
//        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSSSSSSS");
//        Date duration = new Date(millisec);
//        return sdf.format(duration);
//    }

    private static void writeTestRunEventCSV(List<TestRunEvent> events, String filename, String[] headers) throws Exception{
        FileWriter fileWriter = new FileWriter("C:\\Users\\cyprian\\Desktop\\Project\\" + filename + "_data.csv", true);

        final String NEW_LINE_SEPARATOR = "\n";

        CSVPrinter csvPrinter = new CSVPrinter(fileWriter, CSVFormat.EXCEL.withRecordSeparator(NEW_LINE_SEPARATOR).withHeader(headers));
        for(TestRunEvent event : events){
            Set<TestCaseResult> testCaseResults = event.Tests;
            for (TestCaseResult tr : testCaseResults){
                csvPrinter.printRecord(event.ActiveDocument, event.ActiveWindow, event.TriggeredAt,
						event.Duration.toMillis(),tr.Result.toString());
            }
        }
        csvPrinter.flush();
    }


}