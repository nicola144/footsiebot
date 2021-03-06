package footsiebot;

import footsiebot.ai.*;
import footsiebot.database.*;
import footsiebot.datagathering.*;
import footsiebot.gui.*;
import footsiebot.nlp.*;
import java.io.*;
import java.util.*;
import java.lang.Math;
import java.time.LocalDateTime;
import javafx.animation.*;
import javafx.application.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.control.*;


public class Core extends Application {
    private GUIcore ui;
    private INaturalLanguageProcessor nlp;
    private IDatabaseManager dbm;
    private IDataGathering dgc;
    private IIntelligenceUnit ic;

    public static final long DATA_REFRESH_RATE = 900000; //Rate to call onNewDataAvailable in milliseconds
    public long TRADING_TIME = 54000000; //The time of day in milliseconds to call onTradingHour.

    public Double LARGE_CHANGE_THRESHOLD = 0.5;//Large change threshold for use in the IC

    public String USER_NAME = "Dave";//The name of the user (gets loaded from file)
    private Boolean nameless = false;//Whether or not the user currently has a name assigned.

    public static final long DOWNLOAD_RATE = 120000;//Download new data every 120 seconds.

    public Boolean FULLSCREEN = false;

    /*
    * The latest scrape result downloaded, and some boolean "locks" to assist
    * with synchronization. Booleans probably not technically required.
    */

    private volatile ScrapeResult lastestScrape;
    private Boolean freshData = false;
    private Boolean readingScrape = false;
    private Boolean writingScrape = false;

    /*
    * Variables to keep track of what has recently been output, so that we never
    * output a suggestion about something that has just been displayed.
    */
    private ArrayList<Intent> extraDataAddedToLastOutput;
    private String lastOperandOutput;

	private Thread voiceThread;
	public volatile Boolean closing = false;
	private String mostRecentVoiceInput = "";
    public Boolean novoice = false;//Whether or not the program is to be run without voice input.

   /**
    * Constructor for the core
    */
    public Core() {
        nlp = new NLPCore();
        dbm = new DatabaseCore();
        dgc = new DataGatheringCore();
        ic = new IntelligenceCore(dbm);
    }

   /**
    * Launches the application
    *
    * @param args command-line arguments
    */
    public static void main(String[] args) {
        launch(args);
    }

   /**
    * Starts the application
    *
    * @param primaryStage the inital stage of the application
    */
    @Override
    public void start(Stage primaryStage) {
        readSettings();//Loading from the config file
        List<String> args = getParameters().getRaw();
        //Allows running of tests.
        Boolean runTradingHourTest = false;
        Boolean runIntentTest = false;
        if (args.size() > 0) {
            if (args.get(0).equals("tradinghour")){
                runTradingHourTest = true;
            } else if (args.get(0).equals("intenttest")){
                runIntentTest = true;
            }
            else if (args.get(0).equals("novoice")){
                novoice = true;
            }
        }

        //construct UI
        try { //attempt to use custom styling
            File fl = null;
            Scanner sc = null;
            String style = null;
            try {
                fl = new File("src/gui/config/settings.txt");
                sc = new Scanner(fl);
                while (sc.hasNextLine()) {
                    String tmp = sc.nextLine();
                    if (tmp.startsWith("-"))
                        style = tmp.substring(1);
                }
                if (style != null)
                    ui = new GUIcore(primaryStage, style, this);
                else
                    ui = new GUIcore(primaryStage, this);
            } catch (Exception e) {
                Alert err = new Alert(Alert.AlertType.ERROR, "Styling could not be found", ButtonType.OK);
                err.showAndWait();
                ui = new GUIcore(primaryStage, this);
            }
        } catch (Exception e) { //if any exceptions, create with default styling
            Alert err = new Alert(Alert.AlertType.ERROR, "Styling could not be found", ButtonType.OK);
            err.showAndWait();
            // System.out.println(e.getMessage()); //DEBUG
            ui = new GUIcore(primaryStage, this);
        }


        if(!nameless){
            ui.displayMessage("Hello "+USER_NAME+"! Welcome to Footsiebot! How can I help you?");
        }else{
            ui.displayMessage("Hi there! I am Footsiebot! Before we continue, what is your name?");
        }

        if(runTradingHourTest){
            try{
                onTradingHour();//DEBUG
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        if (runIntentTest){
            try{
                testIntents(args.get(1));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if(!novoice){
    		voiceThread = new Thread(() -> {

                try {
    				voce.SpeechInterface.init("./src/voce-0.9.1/lib", false, true, "./src", "grammar");
    				while (!closing)
    				{
    					Thread.sleep(200);
    					testVoce();
    				}
                }
                catch (InterruptedException e){
                    closing = true;
                    System.out.println("Voce received interrupt");
                    return;//Maybe?
                }
                catch (Exception e) {
                    // should not be able to get here...
                    System.out.println("Error in voce");
                    e.printStackTrace();
                }
    			finally{
    				voce.SpeechInterface.destroy();
    			}
            },"voce");
            voiceThread.start();
        }
    }

   /**
    * Tests the speech input
    */
	private void testVoce() {
		while (voce.SpeechInterface.getRecognizerQueueSize() > 0) {
			String s = voce.SpeechInterface.popRecognizedString();

			if(Thread.currentThread().isInterrupted()){
				closing = true;
				return;
			}

			System.out.println("You said: " + s);
			if(s.startsWith("assistant"))
			{
				String query = s.replaceFirst("assistant","");
				System.out.println("Will execute: " + query);
				synchronized(mostRecentVoiceInput){
					mostRecentVoiceInput = query;
				}
			}
			//voce.SpeechInterface.synthesize(s);
		}

	}

   /**
    * Runs the voice input on startup
    */
	public void runVoiceInput() {
		//System.out.println("Checked for voice input");
		String temp = null;
		synchronized(mostRecentVoiceInput){
			if(!mostRecentVoiceInput.isEmpty()){
				temp = mostRecentVoiceInput;
				mostRecentVoiceInput = "";
			}
		}
		if(temp != null){
			ui.displayMessage("VoiceBot thinks you said: "+temp);
			onUserInput(temp);
		}
	}

   /**
    * Performs shut down operations
    */
    @Override
    public void stop() {
        //TODO store the trading hour somewhere
        //TODO write volatile data to the Database
        ui.stopDataDownload();
        if(!novoice){
    		voiceThread.interrupt();
    		try{
    			voiceThread.join();
    		}
    		catch(Exception e){
    			e.printStackTrace();
    		}
        }

        System.out.println("Safely closed the program.");
    }

   /**
    * Deals with the small set of commands that the user can enter.
    * Checks if the raw input is a command, and executes it if it is.
    * Returns a boolean representing whether or not a command was executed.
    *
    * @param raw the raw input from the user
    * @return true if a command was run, otherwise false
    */
    private Boolean handleCommand(String raw) {
        Boolean ranCommand = false;

        if(nameless){
            handleUserNameChange(raw);
            String message = "If this is not your name, or you decide you want";
            message += " me to call you something else, just say 'Call me YOURNAME'";
            message += " at any point.";
            ui.displayMessage(message);
            ranCommand = true;
        }
        else if(raw.toLowerCase().startsWith("call me ")){
            String newName = raw.substring(8, raw.length());
            handleUserNameChange(newName);
            ranCommand = true;
        }
        else if(raw.toLowerCase().contains("tell me a joke")) {
            readJoke();
            ranCommand = true;
        }
        else if(raw.toLowerCase().startsWith("help")) {
            String message = "Ok "+USER_NAME;
            message += " I can:\n";
            message += "Fetch you the spot price of a company.\n    Ask me 'What is the spot price of X?'";
            message += "\n\nFetch you the opening price of a company.\n    Ask me 'What was the opening price of X?'";
            message += "\n\nFetch you the closing price of a company.\n    Ask me 'What was the closing price of X yesterday?'";
            message += "\n\nFetch you the trading volume of a company.\n    Ask me 'What is the volume of X?'";
            message += "\n\nFetch you the percentage change of a company since the market opened.\n    Ask me 'What is the percentage change of X?'";
            message += "\n\nFetch you the absolute change of a company since the market opened.\n    Ask me 'What is the absolute change of X?'";
            message += "\n\nFetch you the trend of a company since the market opened.\n    Ask me 'Is X rising or falling?'";
            message += "\n\nFetch you the trend of a company on a given day.\n    Ask me 'What was the trend in X last Wednesday?'";
            message += "\n\nFetch you the trend of a company since a given day.\n    Ask me 'Has X risen since Tuesday?'";
            message += "\n\nFetch you the recent news on a company.\n    Ask me 'What is the news for X'";
            message += "\n\nGive you most of the above information for a group of companies, and for any day over the past 5 trading days.";
            message += "\n    For example, ask me 'Did Pharmaceuticals rise yesterday?'";
            ui.displayMessage(message);
            ranCommand = true;
        }
        return ranCommand;
    }

   /**
    * Executes the request input by the user
    *
    * @param raw the String input by the user
    */
    public void onUserInput(String raw) {
        if(handleCommand(raw)){
            return;
        }
        onNewDataAvailable();//Checks if new data. If not, does nothing
        ParseResult pr = nlp.parse(raw);
        //Checking the parse result.
        if(pr == null){
            ui.displayMessage("I'm sorry "+USER_NAME+", but I'm afraid I can't understand your input. Try asking 'help' to see what I can do.");
            return;
        }
        else if(pr.getOperand()== null){
            if(pr.getIntent() == Intent.NEWS){
                outputJustNewsSummary();
                return;
            }
            ui.displayMessage("I'm sorry "+USER_NAME+", but I'm afraid I can't understand your input. Try asking 'help' to see what I can do.");
            return;
        }
        else if (pr.getIntent() == null){
            //At this point, we have established that the operand is not null, but the intent is.
            //If the user enters a company without an intent, we give them a summary.
            if (!pr.isOperandGroup()){
                String summary = getSingleCompanySummary(pr.getOperand());
                if (summary != null){
                    ui.displayMessage("I didn't recognise any commands or queries in your input, but I think you wanted to know about "+summary);
                    return;
                }
            }
            ui.displayMessage("I'm sorry "+USER_NAME+", but I'm afraid I can't understand your input. Try asking 'help' to see what I can do.");
            return;
        }
        System.out.println(pr); //DEBUG
        String errorMessage = checkParseResultValid(pr);
        if(errorMessage != null){
            ui.displayMessage(errorMessage);
            return;
        }



        extraDataAddedToLastOutput = null;//Reseting this.

        Suggestion suggestion;

        Boolean managedToStoreQuery = dbm.storeQuery(pr,LocalDateTime.now());
        if(!managedToStoreQuery){
            System.out.println("Failed to store query!");
        }
        else{
            ic.onUpdatedDatabase(LARGE_CHANGE_THRESHOLD.floatValue());//Updating the AI with the newly stored query.
        }

        //Branch based on whether the intent is for news or data.
        if (pr.getIntent() == Intent.NEWS) {
            outputNews(pr,null);
        } else {
            outputFTSE(pr,null);
        }

        lastOperandOutput = pr.getOperand();

        suggestion = ic.getSuggestion(pr);
        if(suggestion != null){
            // DEBUG
            System.out.println(suggestion.getParseResult().getIntent());
            handleSuggestion(suggestion,pr);
        }
        else{
            System.out.println("Null suggestion");
        }
    }

   /**
    * Obtains an array of all the company codes representing
    * companies in the given group
    *
    * @param group The name of the group to fetch constituents for.
    */
    private String[] groupNameToCompanyList(String group) {
        return dbm.getCompaniesInGroup(group);
    }

   /**
    * Returns a string that is the formatted output for a given query, potentially
    * with extra data added.
    *
    * @param data an array of string data representing the answer to the query.
    * @param pr the ParseResult that triggered this query.
    * @param wasSuggestion a Boolean flag to indicate whether the triggering
    *                      ParseResult originated from the IC.
    * @return a formatted string, ready to be output to the user.
    */
    private String formatOutput(String[] data, ParseResult pr, Boolean wasSuggestion) {
        String output = "Whoops, I don't seem to have the data you asked for!";
        switch(pr.getIntent()){
            case SPOT_PRICE:
                output = "The spot price of " + pr.getOperand().toUpperCase() + " is "+ data[0];
                if(!wasSuggestion){
                    output = addExtraDataToOutput(output,data);
                }
                break;
            case TRADING_VOLUME:
                output = "The trading volume of " + pr.getOperand().toUpperCase() + " is "+ data[0];
                if(!wasSuggestion){
                    output = addExtraDataToOutput(output,data);
                }
                break;
            case PERCENT_CHANGE:
                output = "The percentage change of " + pr.getOperand().toUpperCase() + " is "+ data[0]+"% since the market opened.";
                if(!wasSuggestion){
                    output = addExtraDataToOutput(output,data);
                }
                break;
            case ABSOLUTE_CHANGE:
                output = "The absolute change of " + pr.getOperand().toUpperCase() + " is "+ data[0] + " since the market opened.";
                if(!wasSuggestion){
                    output = addExtraDataToOutput(output,data);
                }
                break;
            case OPENING_PRICE:
                {
                    String date = data[1].split("\\|")[1].trim();
                    String[] dateComponents = date.split("-");
                    date = " (" + dateComponents[2] + "-" + dateComponents[1] + "-" + dateComponents[0] + ")";
                    output = "The opening price of "+ pr.getOperand().toUpperCase() +" was " + data[0] + " "+ pr.getTimeSpecifier().toString().toLowerCase().replace("_"," ") + date;
                    if(!wasSuggestion){
                        String[] remainingData = Arrays.copyOfRange(data, 1, data.length);
                        output = addExtraDataToOutput(output,remainingData);
                    }
                }
                break;
            case CLOSING_PRICE:
                {
                    String date = data[1].split("\\|")[1].trim();
                    String[] dateComponents = date.split("-");
                    date = " (" + dateComponents[2] + "-" + dateComponents[1] + "-" + dateComponents[0] + ")";
                    output = "The closing price of "+ pr.getOperand().toUpperCase() +" was " + data[0] + " " + pr.getTimeSpecifier().toString().toLowerCase().replace("_"," ")+ date;
                    if(!wasSuggestion){
                        String[] remainingData = Arrays.copyOfRange(data, 1, data.length);
                        output = addExtraDataToOutput(output,remainingData);
                    }
                }
                break;
            case TREND:
                if(data.length <4){
                    break;
                }
                if(pr.getTimeSpecifier() == TimeSpecifier.TODAY){
                    output = "So far today, "+ pr.getOperand().toUpperCase() + " is ";
                    switch(data[1]){
                        case "rose":
                        output += "rising";
                        break;
                        case "fell":
                        output += "falling";
                        break;
                        case "had no overall change":
                        output += "displaying no net change";
                        break;
                        default:
                        output += "indeterminate";
                        break;
                    }
                    output += " with a net change of "+data[0].trim() + "%.\n";
                    output += "The opening price was "+ data[2] + " and the most recent price is "+ data[3] + ".";
                }
                else{
                    output = pr.getTimeSpecifier().toString().substring(0, 1).toUpperCase() + pr.getTimeSpecifier().toString().substring(1).toLowerCase().replace("_"," ")+", "+ pr.getOperand().toUpperCase();
                    output += " "+data[1];
                    output += " with a net change of "+data[0].trim() + "%.\n";
                    output += "The opening price was "+ data[2] + " and the closing price was "+ data[3] + ".";
                }
                break;
                case TREND_SINCE:
                    if(data.length <4){
                        break;
                    }
                    output = "Since "+pr.getTimeSpecifier().toString().toLowerCase().replace("_"," ")+", "+ pr.getOperand().toUpperCase();
                    output += " "+data[1];
                    output += " with a net change of "+data[0].trim() + "%.\n";
                    output += "The opening price was "+ data[2] + " and the current spot price is "+ data[3] + ".";

                    break;
            case NEWS:
                //Nothing to do here, should never run, TODO remove
                break;
            case GROUP_FULL_SUMMARY:
                if(data.length <6){
                    break;
                }
                if(pr.getTimeSpecifier() == TimeSpecifier.TODAY){
                    output = "So far today, " + pr.getOperand() + " are ";
                    switch(data[1]){
                        case "rose":
                        output += "rising";
                        break;
                        case "fell":
                        output += "falling";
                        break;
                        case "had no overall change":
                        output += "displaying no net change";
                        break;
                        default:
                        output += "indeterminate";
                        break;
                    }
                    output += " with a net change of "+data[0].trim() + "%.\n";
                    String[] high = data[2].split("\\|");
                    output += high[0].trim().toUpperCase() + " has the highest spot price at " + high[1].trim() + ".\n";
                    String[] low = data[3].split("\\|");
                    output += low[0].trim().toUpperCase() + " has the lowest spot price at " + low[1].trim()+ ".\n";
                    String[] mostRising = data[4].split("\\|");
                    output += mostRising[0].trim().toUpperCase() + " has the greatest percentage change at " + mostRising[1].trim()+ "%.\n";
                    String[] mostFalling = data[5].split("\\|");
                    output += mostFalling[0].trim().toUpperCase() + " has the lowest percentage change at " + mostFalling[1].trim()+ "%.";
                }
                else{
                    output = pr.getTimeSpecifier().toString().substring(0, 1).toUpperCase() + pr.getTimeSpecifier().toString().substring(1).toLowerCase().replace("_"," ")+", "+ pr.getOperand()+" ";
                    output += data[1] + " with a net change of "+data[0].trim() + "%.\n";
                    String[] high = data[2].split("\\|");
                    output += high[0].trim().toUpperCase() + " had the highest closing price at " + high[1].trim() + ".\n";
                    String[] low = data[3].split("\\|");
                    output += low[0].trim().toUpperCase() + " had the lowest closing price at " + low[1].trim()+ ".\n";
                    String[] mostRising = data[4].split("\\|");
                    output += mostRising[0].trim().toUpperCase() + " had the greatest percentage change at " + mostRising[1].trim()+ "%.\n";
                    String[] mostFalling = data[5].split("\\|");
                    output += mostFalling[0].trim().toUpperCase() + " had the lowest percentage change at " + mostFalling[1].trim()+ "%.";
                }
                break;
            default:
            System.out.println("No cases ran in core");
            break;
        }

        if (wasSuggestion){
            output = "You may also want to know:\n" + output;
        }

        return output;
    }

   /**
    * Decodes a Suggestion and performs relevant output
    *
    * @param suggestion the Suggestion to be handled
    * @param pr the ParseResult from the initial user message
    */
    private void handleSuggestion(Suggestion suggestion, ParseResult pr) {

        if (suggestion.isNews()) {
            outputNews(suggestion.getParseResult(),suggestion);//Outputting the news for the suggestion
            ui.displayMessage("You may wish to view the news for "+suggestion.getParseResult().getOperand().toUpperCase() + " in the news pane",suggestion);
        } else {
            //System.out.println(suggestion.getParseResult());//DEBUG
            ParseResult suggPr = suggestion.getParseResult();
            if((extraDataAddedToLastOutput != null)&& lastOperandOutput.equals(suggPr.getOperand())){
                //System.out.println(suggPr.getIntent()+" vs "+pr.getIntent());//DEBUG
                if((suggPr.getIntent() == pr.getIntent()) ||extraDataAddedToLastOutput.contains(suggPr.getIntent())){
                    //The intent suggested has already been displayed to the user.
                    return;
                }
            }
            if(pr.getIntent()== Intent.TREND){
                if(pr.getTimeSpecifier()== TimeSpecifier.TODAY){
                    if((suggPr.getIntent() == Intent.SPOT_PRICE)||(suggPr.getIntent() == Intent.OPENING_PRICE)){
                        return;//A trend includes spot price and opening price, so don't bother suggesting these.
                    }
                }
            }
            outputFTSE(suggPr,suggestion);
            System.out.println("Displayed suggestion for pr = "+suggPr.toString());//DEBUG
        }
    }

   /**
    * Outputs news items to the GUI
    *
    * @param pr the ParseResult to be outputted
    * @param s the Suggestion to be outputted
    */
    private void outputNews(ParseResult pr, Suggestion s) {
        Article[] result;
        if (pr.isOperandGroup()) {
            String[] companies = groupNameToCompanyList(pr.getOperand());

            result = dgc.getNews(companies);
        } else {
            result = dgc.getNews(pr.getOperand());
        }
        ui.displayResults(result, s);
    }

   /**
    * Outputs FTSE data to the GUI
    *
    * @param pr the ParseResult to be outputted
    * @param s the Suggestion to be outputted
    */
    private void outputFTSE(ParseResult pr, Suggestion s) {
        /*
        NOTE: may wish to branch for groups, using an overloaded/modified method
        of getFTSE(ParseResult,Boolean).
        */
        String[] data = dbm.getFTSE(pr);

        String result;//NOTE: May convert to a different format for the UI
        Boolean wasSuggestion = (s!= null);
        if (data == null) {
            System.out.println("NULL DATA!");
            if (wasSuggestion) {
                //ui.displayMessage("Sorry, something went wrong trying to give a suggestion for your query");
            } else {
                ui.displayMessage("Whoops, I don't seem to have the data you asked for!");
            }
            return;
        }

        if (pr.isOperandGroup()) {
            result = formatOutput(data, pr, wasSuggestion);
            ui.displayMessage(result, s);
        } else {
            result = formatOutput(data, pr, wasSuggestion);
            ui.displayMessage(result, s);
        }
    }

   /**
    * Adds data to an output String
    *
    * @param output the original String to be appended
    * @param data the data to be added to the output String
    * @return a String with the extra data appended
    */
    private String addExtraDataToOutput(String output, String[] data) {
        extraDataAddedToLastOutput = null;
        if (data.length > 1){
            output += "\n\n";
            extraDataAddedToLastOutput = new ArrayList<Intent>();
            output += "Related data about this company:";
            String[] temp;
            for(int i = 1; i < data.length;i++){
                temp = data[i].split("\\|");//relying on data being sepparated by |. Escaped as regex
                output += "\n" + temp[0] + " = " + temp[1];
                extraDataAddedToLastOutput.add(convertColumnNameToIntent(temp[0]));
            }
        }
        return output;
    }

   /**
    * Converts a column name to an Intent
    *
    * @param s the column name to be converted
    * @return the Intent representation of the column
    */
    private Intent convertColumnNameToIntent(String s) {
        Intent in = nlp.parse(s).getIntent();
        return in;
    }


   /**
    * Checks to see if a ParseResult is valid
    *
    * @param pr the ParseResult to be checked
    * @return null if the ParseResult is valid, otherwise an error message
    */
    private String checkParseResultValid(ParseResult pr){
        String sorry = "Sorry "+USER_NAME + ", ";

        switch (pr.getIntent()){
            case SPOT_PRICE:
                if(pr.isOperandGroup()){
                    return sorry+"I can't give a spot price for a group.";
                }
                if(pr.getTimeSpecifier() != TimeSpecifier.TODAY){
                    return sorry+"I can't give a spot price for any day other than today.";
                }
            break;
            case TRADING_VOLUME:
                if(pr.isOperandGroup()){
                    return sorry+"I can't give a trading volume for a group.";
                }
                if(pr.getTimeSpecifier() != TimeSpecifier.TODAY){
                    return sorry+"I can't give a trading volume for a day other than today.";
                }
            break;
            case OPENING_PRICE:
                if(pr.isOperandGroup()){
                    return sorry+"I can't give an opening price for a group.";
                }
            break;
            case CLOSING_PRICE:
                if(pr.isOperandGroup()){
                    return sorry+"I can't give a closing price for a group.";
                }
                if(pr.getTimeSpecifier() == TimeSpecifier.TODAY){
                    return sorry+"I can't give a closing price for today. Ask for spot price instead.";
                }
            break;
            case PERCENT_CHANGE:
                if(pr.isOperandGroup()){
                    return sorry+"I can't give a % change for a group.";
                }
                if(pr.getTimeSpecifier() != TimeSpecifier.TODAY){
                    return sorry+"I can't give a % change for a day other than today. Perhaps ask whether "+pr.getOperand()+ " rose "+ pr.getTimeSpecifier().toString().replace("_"," ").toLowerCase();
                }
            break;
            case ABSOLUTE_CHANGE:
                if(pr.isOperandGroup()){
                    return sorry+"I can't give an absolute change for a group.";
                }
                if(pr.getTimeSpecifier() != TimeSpecifier.TODAY){
                    return sorry+"I can't give an absolute change for a day other than today.";
                }
            break;
            case TREND:
                if(pr.isOperandGroup()){
                    return sorry+"I can't give a trend for a group.";
                }
            break;
            case TREND_SINCE:
                if(pr.isOperandGroup()){
                    return sorry+"I can't give a trend for a group.";
                }
                if(pr.getTimeSpecifier() == TimeSpecifier.TODAY){
                    return sorry+"I can't give a trend since today. Perhaps ask for just today's trend.";
                }
            break;
            case NEWS:
                if(pr.getTimeSpecifier() != TimeSpecifier.TODAY){
                    return sorry+"I can't give anything except the most up to date news.";
                }
            break;
            case GROUP_FULL_SUMMARY:
                if(!pr.isOperandGroup()){
                    return sorry+"I can't give a group summary for a single company. Maybe ask for the company trend.";
                }
            break;
            default:
            return sorry+"I could not understand your query.";
        }
        return null;
    }

   /**
    * Must only be called asynchronously from the GUIcore.
    * Downloads new data to a local variable in the background.
    */
    public void downloadNewData() throws InterruptedException {
        System.out.println("Downloading new data");
        while(readingScrape){
            System.out.println("Waiting for data to be read");
            try{
                Thread.sleep(1000);
            }catch(Exception e){

            }
        }
        writingScrape = true;
        ScrapeResult temp = dgc.getData();
        if (Thread.interrupted()||Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        if(temp == null){
            return;
        }
        if((lastestScrape == null)){
            lastestScrape = temp;
            freshData = true;
            System.out.println("Data downloaded successfully");
        }
        else if(temp.equals(lastestScrape)){
            System.out.println("Data hasn't changed since last download");
        }
        else{
            synchronized (lastestScrape){//Eliminates potential race conditions on setting/reading lastestScrape
                lastestScrape = temp;
                freshData = true;
            }
            System.out.println("Data downloaded successfully");
        }

        writingScrape = false;
    }

   /**
    * Fetches and stores the latest data, then calls onUpdatedDatabase() from
    * the IC
    */
    public void onNewDataAvailable() {
        if(freshData == false){
            return;
        }
        System.out.println("New data available!");//DEBUG

        if(writingScrape){
            System.out.println("Couldn't retrieve new data, as it was being written");
            return;
        }
        if(lastestScrape == null){
            return;
        }
        readingScrape = true;
        synchronized (lastestScrape){//Should make this section safe
            ScrapeResult sr = lastestScrape;
            // for(int i = 0; i < 101;i++){
            //     System.out.println("Entry " + i+ " is "+sr.getName(i) + " with code " + sr.getCode(i));
            // }
            System.out.println("Data collected.");
            if(!dbm.storeScraperResults(sr)){
                System.out.println("Couldn't store data to database");
            }
        }
        freshData = false;
        readingScrape = false;
        Suggestion[] suggarr = ic.onUpdatedDatabase(LARGE_CHANGE_THRESHOLD.floatValue());
        handleLargeChangeSuggestions(suggarr);
    }

   /**
    * Outputs suggestions created when a large change is detected
    *
    * @param suggarr the suggestion to be outputted
    */
    private void handleLargeChangeSuggestions(Suggestion[] suggarr) {
        if(suggarr == null){
            return;
        }
        String output;
        for (int i = 0;i < suggarr.length ;i++ ) {
            ParseResult pr = suggarr[i].getParseResult();
            String[] data = dbm.getFTSE(pr);
            output = "WARNING: "+pr.getOperand().toUpperCase()+" has a percentage change of " + data[0] +"% which is above the threshold of +-"+ LARGE_CHANGE_THRESHOLD+"%";
            ui.displayMessage(output);//NOT passing the suggestion, as this cannot be marked irrelevant.
        }
    }

   /**
    * Performs the operations necessary when the daily summary is requested
    */
    public void onTradingHour() {
        System.out.println("It's time for your daily summary!");//DEBUG
        Company[] companies = ic.onNewsTime();
        String[] companyCodes = new String[companies.length];
        String output = "Hi "+USER_NAME+", it's time for your daily summary!\nI've detected that the following companies are important to you:";
        if((companies == null) || (companies.length < 1)){
            ui.displayMessage("Sorry "+USER_NAME+", I tried to give you your daily summary, but it appears that I don't have sufficient data for that right now.");
            return;
        }

        for(int i = 0;i < companies.length;i++){
            String summary = getSingleCompanySummary(companies[i].getCode());
            companyCodes[i] = companies[i].getCode();
            if(summary != null){
                output += "\n"+summary;
            }
        }
        output += "\nYou may also view the latest news for these companies in the news pane";
        Article[] news = dgc.getNews(companyCodes);
        ui.displayMessage(output);
        ui.displayResults(news,null);
    }

   /**
    * Returns a string containing a summary for a given company
    *
    * @param code the code of the company
    * @return the summary for the company requested
    */
    private String getSingleCompanySummary(String code) {
        String output = "";
        String[] data = dbm.getFTSE(new ParseResult(Intent.SPOT_PRICE,"trading hour",code,false,TimeSpecifier.TODAY));
        if(data == null){
            return null;
        }
        String[] temp;
        output+= code.toUpperCase()+":\n";
        output+= "    Spot price = "+data[0]+"\n";
        for(int j = 1; j< data.length; j++){
            temp = data[j].split("\\|");
            output+= "    "+temp[0]+" = "+temp[1].trim()+"\n";
        }
        return output;
    }

   /**
    * Outputs just the news part of what would have been the users Trading Hour summary.
    */
    private void outputJustNewsSummary() {
        Company[] companies = ic.onNewsTime();
        String[] companyCodes = new String[companies.length];
        if((companies == null) || (companies.length < 1)){
            ui.displayMessage("Sorry "+USER_NAME+", I think you wanted some news, but I have no idea what companies to give you news for. Please be more specific.");
            return;
        }

        for (int i = 0;i< companies.length;i++ ) {
            companyCodes[i] = companies[i].getCode();
        }
        Article[] news = dgc.getNews(companyCodes);
        ui.displayMessage(USER_NAME+", I think you wanted some news, but I couldn't detect any company or group names in your query, so I've given you news on companies that I think are important to you.");
        ui.displayResults(news,null);
    }

   /**
    * Handles state changes when the user indicates that a suggestion was
    * irrelevant
    *
    * @param s the irrelevant suggestion
    */
    public void suggestionIrrelevant(Suggestion s) {
        System.out.println("A suggestion was marked irrelevant");
        ic.onSuggestionIrrelevant(s);
        ui.displayMessage("Ok " + USER_NAME + ", I will take that into consideration. Thank you for the feedback.");
    }

   /**
    * Handles changes to the username
    *
    * @param name the new username to be stored
    */
    private void handleUserNameChange(String name) {
        USER_NAME = name;
        nameless = false;
        writeSettings(TRADING_TIME,LARGE_CHANGE_THRESHOLD,USER_NAME);
        ui.displayMessage("Thanks "+name+"! How can I help you?");
    }

   /**
    * Handles updating the settings when the user makes a change to them in the GUI
    *
    * @param time the trading time to be stored
    * @param change the change threshold to be stored
    * @param userName the username to be stored
    */
    public void updateSettings(String time, Double change, boolean fullscreen) {
        if (time == null && change == null) {
            return;
        }

        if (change < 0) {//Want absolute value
            change = 0 - change;
        }

        if (time != null) {
            String[] hm = time.split(":");
            Integer hours = Integer.parseInt(hm[0]);
            Integer minutes = Integer.parseInt(hm[1]);
            long newTradingTime = (3600000*hours) +(60000*minutes);

            TRADING_TIME = newTradingTime;
        }

        if (change != null) {
            LARGE_CHANGE_THRESHOLD = change;
        }

        FULLSCREEN = fullscreen;

        ui.stopTradingHourTimeline();
        ui.startTradingHourTimeline();
        writeSettings(TRADING_TIME, LARGE_CHANGE_THRESHOLD, USER_NAME);
        System.out.println("Updating the settings with a time of " + TRADING_TIME + " and a change of " + LARGE_CHANGE_THRESHOLD);
    }

   /**
    * Writes the settings to the config file
    *
    * @param time the trading time to be stored
    * @param change the change threshold to be stored
    * @param userName the username to be stored
    */
    private void writeSettings(Long time, Double change, String userName) {
        File fl = null;
        BufferedWriter bw = null;
        try{
            fl = new File("src/config.txt");
            bw = new BufferedWriter(new FileWriter(fl.getAbsolutePath().replace("\\", "/")));
            bw.write(time.toString());
            bw.newLine();
            bw.write(change.toString());
            bw.newLine();
            bw.write(userName);
            bw.newLine();
            bw.write(FULLSCREEN.toString());
        }catch(Exception e){
            e.printStackTrace();
        }
        finally{
            tryClose(bw);
        }

    }

   /**
    * Reads the settings from the config file
    */
    private void readSettings(){
        File fl = null;
        BufferedReader br = null;
        try{
            fl = new File("src/config.txt");
            br = new BufferedReader(new FileReader(fl.getAbsolutePath().replace("\\", "/")));
            TRADING_TIME = Long.parseLong(br.readLine());
            LARGE_CHANGE_THRESHOLD = Double.parseDouble(br.readLine());
            USER_NAME = br.readLine();
            FULLSCREEN = Boolean.parseBoolean(br.readLine());

        }catch(Exception e){
            e.printStackTrace();
        }
        finally{
            tryClose(br);
        }
        if(USER_NAME == null || USER_NAME.isEmpty()){
            nameless = true;
        }
        System.out.println("Loaded TRADING_TIME as "+TRADING_TIME);
        System.out.println("Loaded LARGE_CHANGE_THRESHOLD as "+LARGE_CHANGE_THRESHOLD);
    }

   /**
    * Opens the webpage at the url given on the user's default browser
    *
    * @param url the url of the webpage
    */
    public void openWebpage(String url) {
        getHostServices().showDocument(url);
    }

   /**
    * Tries to close a Closable object
    *
    * @param c the object to close
    */
    private static void tryClose(Closeable c) {
        try{
            c.close();
        }catch(Exception ex){
        }
    }

   /**
    * Reads a random joke from the joke file
    */
    private void readJoke() {
        File fl = null;
        BufferedReader br = null;
        String complete = null;
        try{
            fl = new File("src/jokes.txt");
            br = new BufferedReader(new FileReader(fl.getAbsolutePath().replace("\\", "/")));
            int rand = (int) Math.ceil(Math.random() * 6);
            for (int i = 0; i < rand; i++) {
                complete = br.readLine();
            }

        }catch(Exception e){
            e.printStackTrace();
        }
        finally{
            tryClose(br);
        }

        final String[] joke = complete.split(",");
        ui.displayMessage(joke[0]);
        Timeline tellJoke = new Timeline();
        tellJoke.getKeyFrames().add(new KeyFrame(Duration.millis(1500), e -> ui.displayMessage(joke[1])));
        try {
            tellJoke.play();
        } catch (Exception e) {

        }

        // ui.displayMessage(joke[1]);

    }

   /**
    * Tests all intents and all time specifiers for a company
    *
    * @param operand the company code
    */
    private void testIntents(String operand) {
        operand = operand.toLowerCase();
        for (Intent i : Intent.values()) {
            for (TimeSpecifier t : TimeSpecifier.values()) {
                ParseResult pr = new ParseResult(i, "", operand, false, t);
                if (checkParseResultValid(pr)==null) {
                    System.out.println("Testing ParseResult: "+pr);
                    switch(i) {
                        case NEWS:
                            outputNews(pr,null);
                            break;
                        default:
                            outputFTSE(pr,null);
                    }
                    try{
                        Thread.sleep(100);
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }
    }

}
