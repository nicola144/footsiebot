package footsiebot;

import footsiebot.nlpcore.*;
import footsiebot.intelligencecore.*;
import footsiebot.datagatheringcore.*;
import footsiebot.guicore.*;
import footsiebot.databasecore.*;
import javafx.application.Application;
import javafx.stage.Stage;
import java.io.*;


public class Core extends Application {
  private GUIcore ui;
  private static INaturalLanguageProcessor nlp;

  public static void main(String[] args) {
    nlp = new NLPCore();

    if(args.length > 0){
        if(args[0].equals("nlp")){
            debugNLP();
            return;
        }
    }

    //Initialise user interface
    launch(args);





  }

  private static void debugNLP(){
    while(true){
        String input = readEntry("Enter a query:\n");

        if (input.equals("exit")){
            break;
        }

        ParseResult result = nlp.parse(input);
        if(result == null){
            System.out.println("Sorry, I did not understand the query.");
        }
        System.out.println(result);
    }
  }

  /**
  * Starts the application
  *
  * @param primaryStage the inital stage of the application
  */
  @Override
  public void start(Stage primaryStage) {
      //construct UI
      try { //attempt to use custom styling
          FileReader fRead = new FileReader("./guicore/config/settings.txt");
          BufferedReader buffRead = new BufferedReader(fRead);
          String tmp = buffRead.readLine();
          if (tmp != null)
              ui = new GUIcore(primaryStage, tmp);
          else
              ui = new GUIcore(primaryStage);
      } catch (Exception e) { //if any exceptions, create with default styling
          // Alert err = new Alert()
          ui = new GUIcore(primaryStage);
      }
  }

  private static String readEntry(String prompt) //Nicked from Databases worksheets, can't be included in final submission DEBUG
	{
		try
		{
			StringBuffer buffer = new StringBuffer();
			System.out.print(prompt);
			System.out.flush();
			int c = System.in.read();
			while(c != '\n' && c != -1) {
				buffer.append((char)c);
				c = System.in.read();
			}
			return buffer.toString().trim();
		}
		catch (IOException e)
		{
			return "";
		}
 	}


}
