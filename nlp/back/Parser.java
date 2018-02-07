package footsiebot.nlp;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.*;
import java.io.FileReader;
import java.io.BufferedReader;

public class Parser implements IParser{
  private TreeMap<String,String> operandMap;//Maps raw string to internally used string. Could map to an enum instead.

  private HashMap<String,Intent> intentMap;//Maps raw string to intent
  private ArrayList<String> intentList;

  private HashMap<String,TimeSpecifier> timeMap;
  private ArrayList<String> timeList;

  public Parser(){
    initialiseOperandMap();
    initialiseIntents();
    initialiseTimes();
  }



  private String stripOperand(String raw){

    return "";//DEBUG
  }

  public ParseResult parse(String s){
    Intent in = Intent.SPOT_PRICE;//DEBUG
    String raw = s;
    String operand = "";//DEBUG
    Boolean operandIsGroup = false;
    TimeSpecifier ts = TimeSpecifier.TODAY;//DEBUG

    s = s.toLowerCase();
	  //STRIP INTENT OUT OF STRING USING STRING.REPLACE AND STRING.CONTAINS
    Boolean valid = false;
    for ( String i: intentList) {
      if(s.contains(i)){
        in = intentMap.get(i);
        s = s.replace(i,"");
        System.out.println("Found intent '"+ in + "' as string '" + i + "' in raw '" + raw + "'");//DEBUG
        valid = true;
        break;
      }
    }

    if(!valid){
      return new ParseResult(null,raw,null,operandIsGroup,null);//Replace with error enums?
    }

    System.out.println("s is now '"+s+"'");

    //FIND TIME SPECIFIER FROM REMAINING.
    valid = false;
    for ( String i: timeList) {
      if(s.contains(i)){
        ts = timeMap.get(i);
        System.out.println("Found TimeSpecifier '"+ ts + "' as string '" + i + "' in s '" + s + "'");//DEBUG
        s = s.replace(i,"");
        valid = true;
        break;
      }
    }
    //Acceptable for there to be no time specifier.
    System.out.println("s is now '"+s+"'");



	  //TOKENIZE REMAINING STRING



	   //FIND OPERAND, AND REMOVE IT.








    return new ParseResult(in,raw,operand,operandIsGroup,ts);
  }

  private void initialiseTimes(){
    timeMap = new HashMap<String,TimeSpecifier>();
    timeList = new ArrayList<String>();
    timeMap.put("today",TimeSpecifier.TODAY);
    timeMap.put("yesterday",TimeSpecifier.YESTERDAY);
    timeMap.put("monday",TimeSpecifier.LAST_MONDAY);
    timeMap.put("tuesday",TimeSpecifier.LAST_TUESDAY);
    timeMap.put("wednesday",TimeSpecifier.LAST_WEDNESDAY);
    timeMap.put("thursday",TimeSpecifier.LAST_THURSDAY);
    timeMap.put("friday",TimeSpecifier.LAST_FRIDAY);

    for (String s :timeMap.keySet()) {
      timeList.add(s);
    }
    timeList.sort(String.CASE_INSENSITIVE_ORDER);

    // TODAY,
    // YESTERDAY,
    // LAST_MONDAY,
    // LAST_TUESDAY,
    // LAST_WEDNESDAY,
    // LAST_THURSDAY,
    // LAST_FRIDAY


  }

  private void initialiseIntents(){
    intentMap = new HashMap<String,Intent>();
    intentList = new ArrayList<String>();
    //Adding intents and their synonyms manually, as they should remain fixed
    intentMap.put("spot",Intent.SPOT_PRICE);
    intentMap.put("current",Intent.SPOT_PRICE);
    //intentMap.put("price",Intent.SPOT_PRICE);//Maybe not needed?

    intentMap.put("trading volume",Intent.TRADING_VOLUME);
    intentMap.put("volume",Intent.TRADING_VOLUME);

    intentMap.put("opening",Intent.OPENING_PRICE);

    intentMap.put("closing",Intent.CLOSING_PRICE);

    intentMap.put("percentage change",Intent.PERCENT_CHANGE);
    intentMap.put("percent change",Intent.PERCENT_CHANGE);
    intentMap.put("% change",Intent.PERCENT_CHANGE);

    intentMap.put("absolute change",Intent.ABSOLUTE_CHANGE);
    intentMap.put("abs change",Intent.ABSOLUTE_CHANGE);

    intentMap.put("rising",Intent.TREND);
    intentMap.put("falling",Intent.TREND);
    intentMap.put("risen",Intent.TREND);
    intentMap.put("fallen",Intent.TREND);
    //Group stuff more difficult.

    for (String s :intentMap.keySet()) {
      intentList.add(s);
    }
    // intentList.add("spot");
    // intentList.add("current");
    //
    // intentList.add("trading volume");
    // intentList.add("volume");
    // intentList.add("opening");
    // intentList.add("closing");
    // intentList.add("percentage change");
    // intentList.add("percent change");
    // intentList.add("% change");
    // intentList.add("absolute change");
    // intentList.add("abs change");
    // intentList.add("rising");
    // intentList.add("falling");
    // intentList.add("risen");
    // intentList.add("fallen");

    intentList.sort(String.CASE_INSENSITIVE_ORDER);

    // SPOT_PRICE,
    // TRADING_VOLUME,
    // OPENING_PRICE,
    // CLOSING_PRICE,
    // PERCENT_CHANGE,
    // ABSOLUTE_CHANGE,
    // TREND,//rising or falling
    // GROUP_TREND,//Are X rising or falling
    // GROUP_GET_RISING,//List those X that are rising
    // GROUP_GET_FALLING,
    // GROUP_FULL_SUMMARY//Summary of group X
  }

  private void initialiseOperandMap(){
    //An operand is a company or group name
    operandMap = new TreeMap<String,String>(String.CASE_INSENSITIVE_ORDER);
    String[] companies = new String[]{};

    try{
      BufferedReader br = new BufferedReader(new FileReader("./operands.csv"));
      String line;
      String[] splitLine;
      while((line = br.readLine()) != null){
        splitLine = line.toLowerCase().split(",");
        String operand = splitLine[0];

        operandMap.put(operand,operand);//Adds the operand to the map, mapping to itself
        //System.out.println("Added '" + operand +"' to the map");//DEBUG

        if(splitLine.length < 2){//There are no synonyms
          continue;//read next line
        }
        else{//There is at least 1 synonym
          for (int i = 1; i< splitLine.length;i++ ) {//Adds all synonyms to map, mapped to the primary operand
            operandMap.put(splitLine[i],operand);
            //System.out.println("Added '"+ splitLine[i] + "' -> '" + operand +"' to the map");//DEBUG
          }
        }
      }

    }catch(Exception e){
      e.printStackTrace();
      System.err.println("Error loading operands");
    }

    /*DEBUG
    //Adding company mappings name-code
    operandMap.put("3i","III");
    operandMap.put("Admiral Group","ADM");
    operandMap.put("Anglo American plc","AAL");
    operandMap.put("Antofagasta","ANTO");
    operandMap.put("Ashtead Group","AHT");
    operandMap.put("Associated British Foods","ABF");
    operandMap.put("AstraZeneca","AZN");
    operandMap.put("Aviva","AV.");
    operandMap.put("BAE Systems","BA.");
    operandMap.put("Barclays","BARC");
    operandMap.put("Barratt Developments","BDEV");
    operandMap.put("Berkeley Group Holdings","BKG");
    operandMap.put("BHP","BLT");
    operandMap.put("BP","BP.");
    operandMap.put("British American Tobacco","BATS");
    operandMap.put("British Land","BLND");
    operandMap.put("BT Group","BT.A");
    operandMap.put("Bunzl","BNZL");
    operandMap.put("Burberry","BRBY");
    operandMap.put("Carnival Corporation & plc","CCL");
    operandMap.put("Centrica","CNA");
    operandMap.put("Coca-Cola HBC AG","CCH");
    operandMap.put("Compass Group","CPG");
    operandMap.put("CRH plc","CRH");
    operandMap.put("Croda International","CRDA");
    operandMap.put("DCC plc","DCC");
    operandMap.put("Diageo","DGE");
    operandMap.put("Direct Line Group","DLG");
    operandMap.put("easyJet","EZJ");
    operandMap.put("Evraz","EVR");
    operandMap.put("Experian","EXPN");
    operandMap.put("Ferguson plc","FERG");
    operandMap.put("Fresnillo plc","FRES");
    operandMap.put("G4S","GFS");
    operandMap.put("GKN","GKN");
    operandMap.put("GlaxoSmithKline","GSK");
    operandMap.put("Glencore","GLEN");
    operandMap.put("Halma","HLMA");
    operandMap.put("Hammerson","HMSO");
    operandMap.put("Hargreaves Lansdown","HL.");
    operandMap.put("HSBC","HSBA");
    operandMap.put("Imperial Brands","IMB");
    operandMap.put("Informa","INF");
    operandMap.put("InterContinental Hotels Group","IHG");
    operandMap.put("International Airlines Group","IAG");
    operandMap.put("Intertek","ITRK");
    operandMap.put("ITV plc","ITV");
    operandMap.put("Johnson Matthey","JMAT");
    operandMap.put("Just Eat","JE");
    operandMap.put("Kingfisher plc","KGF");
    operandMap.put("Land Securities","LAND");
    operandMap.put("Legal & General","LGEN");
    operandMap.put("Lloyds Banking Group","LLOY");
    operandMap.put("London Stock Exchange Group","LSE");
    operandMap.put("Marks & Spencer","MKS");
    operandMap.put("Mediclinic International","MDC");
    operandMap.put("Micro Focus","MCRO");
    operandMap.put("Mondi","MNDI");
    operandMap.put("Morrisons","MRW");
    operandMap.put("National Grid plc","NG.");
    operandMap.put("Next plc","NXT");
    operandMap.put("NMC Health","NMC");
    operandMap.put("Old Mutual","OML");
    operandMap.put("Paddy Power Betfair","PPB");
    operandMap.put("Pearson PLC","PSON");
    operandMap.put("Persimmon plc","PSN");
    operandMap.put("Prudential plc","PRU");
    operandMap.put("Randgold Resources","RRS");
    operandMap.put("Reckitt Benckiser","RB.");
    operandMap.put("RELX Group","REL");
    operandMap.put("Rentokil Initial","RTO");
    operandMap.put("Rio Tinto Group","RIO");
    operandMap.put("Rolls-Royce Holdings","RR.");
    operandMap.put("The Royal Bank of Scotland Group","RBS");
    operandMap.put("Royal Dutch Shell","RDSA");
    operandMap.put("RSA Insurance Group","RSA");
    operandMap.put("Sage Group","SGE");
    operandMap.put("Sainsbury's","SBRY");
    operandMap.put("Schroders","SDR");
    operandMap.put("Scottish Mortgage Investment Trust","SMT");
    operandMap.put("Segro","SGRO");
    operandMap.put("Severn Trent","SVT");
    operandMap.put("Shire plc","SHP");
    operandMap.put("Sky plc","SKY");
    operandMap.put("Smith & Nephew","SN.");
    operandMap.put("Smith, D.S.","SMDS");
    operandMap.put("Smiths Group","SMIN");
    operandMap.put("Smurfit Kappa","SKG");
    operandMap.put("SSE plc","SSE");
    operandMap.put("Standard Chartered","STAN");
    operandMap.put("Standard Life Aberdeen","SLA");
    operandMap.put("St. James's Place plc","STJ");
    operandMap.put("Taylor Wimpey","TW.");
    operandMap.put("Tesco","TSCO");
    operandMap.put("TUI Group","TUI");
    operandMap.put("Unilever","ULVR");
    operandMap.put("United Utilities","UU.");
    operandMap.put("Vodafone Group","VOD");
    operandMap.put("Whitbread","WTB");
    operandMap.put("WPP plc","WPP");



    */
  }

}