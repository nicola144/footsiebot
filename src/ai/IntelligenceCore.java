package footsiebot.ai;

import footsiebot.nlp.ParseResult;
import footsiebot.nlp.Intent;
import footsiebot.nlp.TimeSpecifier;
import footsiebot.database.*;

import java.util.*;
import java.lang.*;


public class IntelligenceCore implements IIntelligenceUnit {
  /**
   *
   */

   // To be possibly set by the user
   private byte TOP = 5;
   private ArrayList<Company> companies;
   private ArrayList<Group> groups;
   private double startupHour;
   private Suggestion lastSuggestion;
   private IDatabaseManager db;

   public IntelligenceCore(IDatabaseManager db) {
     this.db = db;
     onStartUp();
   }


   public Suggestion getSuggestion(ParseResult pr) {
     // Fetch operand and intent and increment intent priority
     // TODO needs converting to AIIntent

     if(companies == null){
         System.out.println("companies was null, cannot make suggestion");//DEBUG
         return null;
     }

     String companyOrGroup = pr.getOperand();
     Group targetGroup = null;
     Company targetCompany = null;
     // TODO: UPDATE TALLIES FOR THIS COMPANY LOCALLY
     // If operand is a group
     if(pr.isOperandGroup()) {
         if(groups == null){
             System.out.println("Groups was null, cannot make suggestion");//DEBUG
             return null;
         }
       // search in groups if valid group
       for(Group g: groups) {
         if(g.getGroupCode().equals(companyOrGroup)) {
           targetGroup = g;
           break;
         }
       }
       // if error will return null
       if(targetGroup == null) return null;
       // for group only suggest news
       boolean doSuggestion = false;
       // check if group is in top 5
       for(int i = 0; i < TOP; i++) {
         if(targetGroup.equals(groups.get(i))) {
           doSuggestion = true;
         }
       }
       if(doSuggestion) {
         lastSuggestion = suggestNews(targetGroup);
         return lastSuggestion;
         // return Group to Core
       } else {
         return null;
       }
     } else {
       // operand is a company
       for(Company c: companies) {
         if(c.getCode().equals(companyOrGroup)) {
           targetCompany = c;
           break;
         }
       }
       if(targetCompany == null){
           System.out.println("No company found for suggestion making");//DEBUG
           return null;
        }

       boolean doSuggestion = false;
       for(int i = 0; i < TOP; i++) {
         if(targetCompany.equals(companies.get(i))) {
           doSuggestion = true;
         }
       }

       if(doSuggestion) {
         // This will need to be modified as
         // it just suggests an intent now
         // but could decide to suggest news
         lastSuggestion = suggestIntent(targetCompany);
         return lastSuggestion;
         // return Group to Core
       } else {
           System.out.println("Decided not to make a suggestion");//DEBUG
         return null;
       }
     }
   }

   //TODO return a suggestion object
   public Suggestion[] onUpdatedDatabase() {
     companies = db.getAICompanies();
     groups = db.getAIGroups();
     // DEBUG
     if(companies == null) {
       return null;
     }
     Collections.sort(companies);
     if(groups == null) {
       return null;
     }
     Collections.sort(groups);

     ArrayList<Company> changed = detectedImportantChange();
     if(changed.size() == 0) return null;

     ArrayList<Suggestion> res = new ArrayList<>();

     for(Company c: changed) {
       // NOTE parseresult is null
       res.add(new Suggestion("Detected important change", c, false, null));
     }

     return res.toArray(new Suggestion[res.size()]);
   }

   public void onShutdown() {
     db.storeAICompanies(companies);
     db.storeAIGroups(groups);
   }

   public void onStartUp() {
     // Fetch from database
     companies = db.getAICompanies();//NOTE: may not be necessary if onNewDataAvailable is called on startup
     groups = db.getAIGroups();
     if(companies != null){
         Collections.sort(companies);
     }
     if(groups  != null){
         Collections.sort(groups);
     }
   }

   /**
    * User has reported that a suggestion has not been relevant
    * ajust weights accordingly
    * @param  String companyOrGroup
    * @return
    */
   public String onSuggestionIrrelevant(AIIntent intent, String companyOrGroup, boolean isNews) {
     String alert = "";
     // check if it is a company or a group
     for(Company c: companies) {
       if(c.getCode().equals(companyOrGroup)) {
         System.out.println("weight "c.getIrrelevantSuggestionWeight());
         //TODO
         c.decrementPriorityOfIntent(intent);
         alert+= "Company " + companyOrGroup + " has been adjusted priority accordingly ";
         return alert;
       }
     }
     // is a group
     for(Group g: groups) {
       if(g.getGroupCode().equals(companyOrGroup)) {
         System.out.println("weight "c.getIrrelevantSuggestionWeight());
         // Overall for groups ?
         g.decrementPriority(g.getIrrelevantSuggestionWeight());
         alert+= "Group " + companyOrGroup + "has been adjusted priority accordingly";
         return alert;
       }
     }

     return "Error, no company nor group matching found";
   }

   /**
    *
    * @return [description]
    */
   public Company[] onNewsTime() {
     // show report about 5 top companies
     // just returns the companies to core ?
     Company[] result = new Company[TOP];
     for(int i = 0; i < TOP; i++) {
       result[i] = companies.get(i);
     }
	   return result;
   }

   // TODO
   private ArrayList<Company> detectedImportantChange() {
     ArrayList<String> names = db.detectedImportantChange();
     if(names.size() == 0) return null;

     ArrayList<Company> winningCompanies = new ArrayList<>();

     for(String s: names) {
       for(Company c: companies) {
         if(s.equals(c.getCode())) {
           winningCompanies.add(c);
         }
       }
     }

     return winningCompanies;
   }

   /**
    *
    * @param  Company company       [description]
    * @return         [description]
    */
   private Suggestion suggestIntent(Company company) {
     String reason = "Company is in top 5";
     // String description = "Suggesting ";

     IntentData topIntent = company.getTopIntentData();
     //Float topIntentValue = topIntent.getLastValue();

     // Create IParseResult
     TimeSpecifier tm = footsiebot.nlp.TimeSpecifier.TODAY;
     if(topIntent.getIntent() == AIIntent.CLOSING_PRICE) {
       tm = footsiebot.nlp.TimeSpecifier.YESTERDAY;
     }

     Intent i = null;

     switch(topIntent.getIntent()) {
       case SPOT_PRICE : i = footsiebot.nlp.Intent.SPOT_PRICE;
       break;
       case OPENING_PRICE : i = footsiebot.nlp.Intent.OPENING_PRICE;
       break;
       case CLOSING_PRICE : i = footsiebot.nlp.Intent.CLOSING_PRICE;
       break;
       case PERCENT_CHANGE : i = footsiebot.nlp.Intent.PERCENT_CHANGE;
       break;
       case ABSOLUTE_CHANGE : i = footsiebot.nlp.Intent.ABSOLUTE_CHANGE;
       break;
     }

     if(i == null) return null;

     ParseResult pr = new ParseResult(i, "", company.getCode(), false, tm);

     // false == suggestion is not news
     Suggestion result = new Suggestion(reason, company, false, pr);
     return result;
   }

   private Suggestion suggestNews(Company company) {
     String reason = "Company is in top 5";
     ParseResult pr = new ParseResult(footsiebot.nlp.Intent.NEWS, "", company.getCode(), false, footsiebot.nlp.TimeSpecifier.TODAY);
     Suggestion result = new Suggestion(reason, company, true, pr);
     return result;
   }

   private Suggestion suggestNews(Group group) {
     String reason = "Group is in top 5";
     ParseResult pr = new ParseResult(footsiebot.nlp.Intent.NEWS, "", group.getGroupCode(), true,footsiebot.nlp.TimeSpecifier.TODAY );
     Suggestion result = new Suggestion(reason, group, pr );
     return result;
   }

   private void updateLastSuggestion() {

   }


}
