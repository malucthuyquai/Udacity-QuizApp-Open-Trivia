package android.example.com.quizapp;

/**
 *  Open Trivia QuizApp is a Udacity EU-Scholarship Project
 *  created by ByteTonight at GitHub.
 *  Questions and answers provided by Open Trivia Database
 *  through a free for commercial use API maintained by PIXELTAIL GAMES LLC
 */

import android.example.com.quizapp.fragments.FragmentCorrectAnswer;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itternet.OpenTDbResponse;
import com.itternet.QuizQuestionFragmentFactory;
import com.itternet.interfaces.Communicator;
import com.itternet.interfaces.OpenTriviaDataBaseAPI;
import com.itternet.models.QuestionsListData;
import com.itternet.models.QuizSessionToken;
import com.itternet.models.QuizSessionTokenReset;
import com.itternet.models.Result;
import java.util.ArrayList;
import de.vogella.algorithms.shuffle.ShuffleArray;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class MainActivity extends AppCompatActivity implements Communicator
{
    //private Fragment questionFragment;
    private static final String QUESTION_FRAGMENT_TAG = "questionFragmentTag";
    public static final String CORRECT_ANSWER_DIALOG_TAG = "CADTag";
    public static final String SESSION_TOKEN = "sessionToken";
    public static final String BASE_URL = "baseUrl";
    public static final String PLAYER_SCORE = "playerScore";
    public static final String QUIZ_LIST_DATA = "qListData";

    private int playerScore = 0;
    private TextView tvCategoryName;
    private Toast toaster;
    private ProgressBar progBar;
    private ProgressDialog pDialog;
    private OpenTriviaDataBaseAPI openTDbAPI = null; //Instantiated on demand. No more eager loading
    private QuestionsListData qListData = null; //The Model holding the list of questions
    private TextView scoreField;



//region Events
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        //Read session token from shared preferences, perhaps there is one stored
        if (QuizConfig.getApiBaseURL() == null)
            QuizConfig.setSessionToken(readStringFromPreferences(SESSION_TOKEN));

        //Get the API-URL from manifest
        if (QuizConfig.getApiBaseURL() == null)
            QuizConfig.setApiBaseURL(readMetaData(BASE_URL));

        //showActionBarIcon();
        setContentView(R.layout.activity_main);
        tvCategoryName = (TextView) findViewById(R.id.tvCurrentCategory);
        progBar = (ProgressBar) findViewById(R.id.progressBar);


        Intent previousIntent = getIntent();
        Bundle extras = null;
        if (previousIntent != null)
            extras = previousIntent.getExtras();

        /**
         * find out if previous intent was category selector.
         * Other Activities shouldn't pass categoryID
         */

        if (extras != null)
        {
            if (extras.containsKey("categoryID"))
                QuizConfig.setCategoryID(previousIntent.getIntExtra("categoryID", -1));
            if (extras.containsKey("categoryName"))
                QuizConfig.setCategoryName(previousIntent.getStringExtra("categoryName"));
        }

        if (savedInstanceState != null)
        {
            //questionFragment = getSupportFragmentManager().findFragmentByTag(QUESTION_FRAGMENT_TAG);
            playerScore = savedInstanceState.getInt(PLAYER_SCORE);
            qListData = savedInstanceState.getParcelable(QUIZ_LIST_DATA);
            displayPlayerScore();
        }
        else
        {
            prepareQuiz();
        }

        if (QuizConfig.getCategoryID() == null)
            tvCategoryName.setText(getString(R.string.any_category));
        else
            tvCategoryName.setText(QuizConfig.getCategoryName());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(PLAYER_SCORE, playerScore);
        outState.putParcelable(QUIZ_LIST_DATA, qListData);
    }


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * Just to say that If your older device has a menu-button, the overflow-icon won't show, on the newer phones the overflow button will show.
     * Duh ?!?!? I was wondering and wondering why on Earth my "3 dots" were not appearing in the action bar
     * @param menu : the Menu instance passed to this event
     * @return : ideally true
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater mnuPump = getMenuInflater();
        mnuPump.inflate(R.menu.navigation_menu, menu);
        //return true;
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * An OnClick-Callback triggered by the Wrong Answer Dialog
     * @param msg : The message returned by the Dialog Button
     */
    @Override
    public void onDialogMessage(String msg)
    {
        //prepareToast(msg);
        if (msg.equals("OK"))
        {
            if (QuizConfig.getCurrentQuestionIndex() <= QuizConfig.getLastQuestionIndex())
                switchQuizFragment(null);
            else
            {
                runResultsActivity();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        //prepareToast(item.getTitle().toString());
        Intent targetIntent;

        switch(item.getItemId())
        {
            case R.id.nav_categories:
                targetIntent = new Intent(MainActivity.this, CategorySelectionActivity.class);
                startActivity(targetIntent);
                finish(); //Finishes the current Activity but I should better reset than finish
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed(){
        super.onBackPressed();
        //prepareQuiz();
        //TODO: Perhaps go to startup ?
        //finish();
    }
//endregion

    /**
     * Start up Retrofit and required components for API communications
     */
    private void initializeQuizAPI()
    {
        Log.d("entered","initializeQuizAPI()");

        Gson gson = new GsonBuilder().create();

        OkHttpClient.Builder okHTTPClientBuilder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        //loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.NONE);

        okHTTPClientBuilder.addInterceptor(loggingInterceptor);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(QuizConfig.getApiBaseURL())
                //.baseUrl(getResources().getString(R.string.api_base_url))
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(okHTTPClientBuilder.build())
                .build();

        openTDbAPI = retrofit.create(OpenTriviaDataBaseAPI.class);

    }

    /**
     * Read metaData from AndroidManfiest.xml
     * @param which key to read from manifest metaData
     * @return String value of key if found, or null
     */
    private String readMetaData(String which)
    {
        try
        {
            PackageItemInfo info = getPackageManager().getActivityInfo(new ComponentName(this, MainActivity.class), PackageManager.GET_META_DATA);
            return info.metaData.getString(which);
        }
        catch (PackageManager.NameNotFoundException e)
        {
            return null;
        }
    }

//region Shared Preferences Handling

    /**
     * Store key,value pairs in Android Shared Preferences
     * @param key to store
     * @param value to store
     */
    private void writeStringToPreferences(String key, String value)
    {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(key, value);
        editor.apply();
    }

    /**
     * Read key,value pairs from Android Shared Preferences
     * @param key to read
     * @return
     */
    private String readStringFromPreferences(String key)
    {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String returnData = sharedPref.getString(key, null);
        //Let's see what we got from shared preferences
        Log.d("readPreferences", key + " = " + returnData);
        return returnData;
    }
//endregion

    /**
     * Call this to have the Actionbar (top of screen) display the launcher icon
     */
    private void showActionBarIcon()
    {
        ActionBar ab = getSupportActionBar();
        ab.setLogo(R.mipmap.ic_launcher);
        ab.setDisplayUseLogoEnabled(true);
        ab.setDisplayShowHomeEnabled(true);
    }


    /**
     * Get a set of Questions and Answers from the Model previously populated by Retrofit
     * Create a new Instance of MultipleChoiceFragment Fragment which accepts Data,
     * Inject question and options
     * Replace FragmentContainer content with newly created Fragment
     *
     * @param v : the view holding the button that originally called this method
     */
    public void switchQuizFragment(View v)
    {
        Fragment questionFragment;
        String question = "A Blank Question";
        ArrayList<String> choices = new ArrayList<>();

        //Check if there are questions at all
        if (qListData != null && !qListData.getResults().isEmpty())
        {
            if (QuizConfig.getCurrentQuestionIndex() <= QuizConfig.getLastQuestionIndex())
            {

                int cIndex = QuizConfig.getCurrentQuestionIndex();
                progBar.setProgress(calcQuizProgress(cIndex));
                //The current Record or DataSet is a type of Result Model
                Result currentRecord = qListData.getResultAtIndex(cIndex);
                question = currentRecord.getQuestion();
                String typeOfQuestion = currentRecord.getType();
                QuizConfig.setCorrectAnswer(currentRecord.getCorrectAnswer());
                choices.add(QuizConfig.getCorrectAnswer());

                for (String answer : currentRecord.getIncorrectAnswers())
                {
                    choices.add(answer);
                }
                //Randomize Array to not always have the correct answer in the first radio button
                ShuffleArray.shuffleList(choices);
                //Log.v("switchFrag","get new Fragment");
                questionFragment = QuizQuestionFragmentFactory.create(typeOfQuestion, question, choices);

                if (questionFragment != null && !questionFragment.isInLayout())
                {
                    //FragmentManager fm = getFragmentManager();
                    FragmentManager fm = getSupportFragmentManager();
                    FragmentTransaction ft = fm.beginTransaction();
                    ft.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left);
                    ft.replace(R.id.fragment_container, questionFragment, QUESTION_FRAGMENT_TAG);
                    ft.commit();

                    //QuizConfig.setNextQuestionIndex();
                }
            }
            /*else
            {
                QuizConfig.resetCurrentQuestionIndex();
            }*/
        }
    }

    /**
     * Returns an integer amount to update the progress bar
     * @param currentIndex : the current question index
     * @return
     */
    private int calcQuizProgress(int currentIndex)
    {
        return (currentIndex + 1) * 100 / QuizConfig.getAmountOfQuestions();
    }

    /**
     * Let the Game begin
     */
    private void prepareQuiz()
    {
        if (null == QuizConfig.getSessionToken())
        {
            loadQuizSessionToken();
        }
        else
        {
            //QuizConfig.resetCurrentQuestionIndex();
            loadQuizQuestions();
        }
    }

    /**
     * During a Quiz Session, a category may not have enough questions for the next request.
     * In that case, reset the session to start over again
     */
    private void resetToken()
    {
        Log.d("entered function", "resetToken");
        Call<QuizSessionTokenReset> qTokenCall = openTDbAPI.resetToken(QuizConfig.getSessionToken());
        //enqueue for async calls, execute for synced calls
        qTokenCall.enqueue(new Callback<QuizSessionTokenReset>()
        {
            @Override
            public void onResponse(Call<QuizSessionTokenReset> call, Response<QuizSessionTokenReset> response)
            {

                if (null != pDialog && pDialog.isShowing())
                    pDialog.dismiss();
                Log.d("resetToken()", "HTTP-Response: " + response.code());
                if (response.code() == 200)
                {   //Server responded with "everything cool"
                    if (response.body().getResponseCode() == OpenTDbResponse.RESPONSE_CODE_SUCCESS)
                    {
                        loadQuizQuestions();
                    }
                }
            }

            @Override
            public void onFailure(Call<QuizSessionTokenReset> call, Throwable t)
            {

            }
        });
    }

    /**
     * Load Session Token from API using Retrofit
     */
    private void loadQuizSessionToken()
    {
        Log.d("entered function", "loadQuizSessionToken");
        if (null == openTDbAPI)
            initializeQuizAPI();

        pDialog = new ProgressDialog(MainActivity.this);
        pDialog.setMessage(getResources().getString(R.string.req_sess_token));
        pDialog.setCancelable(false);
        pDialog.show();

        Call<QuizSessionToken> qTokenCall = openTDbAPI.getQuizSessionToken();
        //enqueue for async calls, execute for synced calls
        qTokenCall.enqueue(new Callback<QuizSessionToken>()
        {
            @Override
            public void onResponse(Call<QuizSessionToken> call, Response<QuizSessionToken> response)
            {
                if (response.code() == 200)
                {
                    //Server responded with "everything cool"
                    if (response.body().getResponseCode() == OpenTDbResponse.RESPONSE_CODE_SUCCESS)
                    {
                        QuizConfig.setSessionToken(response.body().getToken());
                        writeStringToPreferences("sessionToken", QuizConfig.getSessionToken());
                        //Log.d("token", sessionToken);
                        loadQuizQuestions();
                    }
                }
            }

            @Override
            public void onFailure(Call<QuizSessionToken> call, Throwable t)
            {

            }
        });
    }

    /**
     * Load Quiz Data from API using Retrofit
     */
    private void loadQuizQuestions()
    {
        if (null == openTDbAPI)
            initializeQuizAPI();

        Log.d("entered function", "loadQuizQuestions");


        if (pDialog != null && pDialog.isShowing())
            pDialog.dismiss();
        pDialog = new ProgressDialog(MainActivity.this);
        pDialog.setMessage(getResources().getString(R.string.req_questions));
        pDialog.setCancelable(false);
        pDialog.show();


        Call<QuestionsListData> qListDataCall;
        qListDataCall = openTDbAPI.getQuizQuestions(
                QuizConfig.getCategoryID(),
                QuizConfig.getAmountOfQuestions(),
                QuizConfig.getSessionToken(),
                QuizConfig.getDifficulty(),
                QuizConfig.getQuestionType()
        );

        qListDataCall.enqueue(new Callback<QuestionsListData>()
        {
            @Override
            public void onResponse(Call<QuestionsListData> call, Response<QuestionsListData> response)
            {
                Log.d("HTTP Response", "" + response.code());

                if (pDialog != null && pDialog.isShowing())
                    pDialog.dismiss();

                if (response.code() == 200)
                {
                    qListData = response.body();
                    Log.d("loadQuizQuestions", "quiz response :" + qListData.getResponseCode());
                    if (qListData.getResponseCode() == OpenTDbResponse.RESPONSE_CODE_SUCCESS)
                    {
                        //All good here
                        if (qListData.getResults() != null)
                        {
                            int listLength = qListData.getResults().size();
                            playerScore = 0;
                            QuizConfig.setLastQuestionIndex(listLength - 1);
                            QuizConfig.setAmountOfQuestions(listLength);
                            QuizConfig.resetCurrentQuestionIndex();
                            switchQuizFragment(null);
                        }

                    }
                    else
                    {
                        switch (qListData.getResponseCode())
                        {
                            case OpenTDbResponse.RESPONSE_CODE_NO_RESULTS:
                                //Not enough questions for requested amount in category
                                break;

                            case OpenTDbResponse.RESPONSE_CODE_INVALID_PARAM:
                                //Now that Retrofit is configured, this shouldn't actually happen
                                break;

                            case OpenTDbResponse.RESPONSE_CODE_TOKEN_NOT_FOUND:
                                //Request a Token
                                loadQuizSessionToken();
                                break;

                            case OpenTDbResponse.RESPONSE_CODE_TOKEN_EMPTY:
                                //No more questions in Session
                                resetToken();
                                break;
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<QuestionsListData> call, Throwable t)
            {

            }
        });
    }

    /**
     * Evaluates the User's answer from the Question Fragment, processes score, and calls next
     * Questions if necessary.
     * @param submitted : The HTML-encoded answer to the current question submitted in the Fragment for Questions
     */
    public void onFragmentSubmit(String submitted)
    {
        String correctAnswer = Html.fromHtml(QuizConfig.getCorrectAnswer()).toString();
        QuizConfig.setNextQuestionIndex();

        if (isCorrectAnswer(submitted))
        {
            ++playerScore;
            displayPlayerScore();
            playSound(R.raw.right);

            if (toaster != null)
                toaster.cancel();

            if (QuizConfig.getCurrentQuestionIndex() <= QuizConfig.getLastQuestionIndex())
                switchQuizFragment(null);
            else
                runResultsActivity();

        }
        else
        {
            displayCorrectAnswerDialog(correctAnswer);
            playSound(R.raw.wrong);
            //prepareToast(message);
        }
        //prepareToast(""+QuizConfig.getCurrentQuestionIndex());
    }


    private void runResultsActivity()
    {
        //if (QuizConfig.getCurrentQuestionIndex() > QuizConfig.getLastQuestionIndex())
        {
            Intent resultsIntent = new Intent(MainActivity.this, ResultsActivity.class);
            Bundle passData = new Bundle();
            passData.putInt("score", playerScore);
            passData.putInt("questions", QuizConfig.getAmountOfQuestions());
            resultsIntent.putExtras(passData);
            startActivity(resultsIntent);
        }
    }

    /**
     * What the function name says
     */
    private void displayPlayerScore()
    {
        /*if (scoreField == null)
            scoreField = (TextView)findViewById(R.id.score);
        scoreField.setText(""+playerScore);*/
        /*runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //Update Score TextView
                    scoreField.setText(""+playerScore);
                }
            });*/
    }

    /**
     * Displays a Dialog containing the correct answer to the current question.
     * Includes an OK Button to close the Dialog and proceed in the Quiz
     * @param msg : The correct Answer to the current question
     */
    public void displayCorrectAnswerDialog(String msg)
    {
        DialogFragment fca = FragmentCorrectAnswer.newInstance(msg);
        //Due to changes in API 23, Dialogs don't work as they did before, hence line below !
        //fca.setStyle(DialogFragment.STYLE_NORMAL, R.style.CustomDialog);
        //fca.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.CustomDialog);


        fca.setCancelable(false);
        fca.show(getSupportFragmentManager(), CORRECT_ANSWER_DIALOG_TAG);
    }

    /**
     * A simple Wrapper to display Toasts
     * @param msg: The text to display in the Toast
     */
    public void prepareToast(String msg)
    {
        if (toaster != null)
            toaster.cancel();
        toaster = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        toaster.show();
    }

    /**
     * A simple Wrapper to play Sounds
     * @param resource : The resource Id of the sound file to play
     */
    public void playSound(int resource)
    {

        MediaPlayer mediaPlayer = MediaPlayer.create(this, resource);
        mediaPlayer.setOnCompletionListener(
                new MediaPlayer.OnCompletionListener()
                {
                    @Override
                    public void onCompletion(MediaPlayer mp)
                    {
                        mp.reset();
                        mp.release();
                        mp = null;
                    }
                });
        mediaPlayer.start();

    }

    /**
     * Compares the player's submitted answer with the correct answer
     * @param a is the answer submitted by the player
     * @return
     */
    private boolean isCorrectAnswer(String a)
    {
        //if (true) return false;

        return Html.fromHtml(QuizConfig.getCorrectAnswer()).toString().equals(a);
    }


    private static class QuizConfig {
        private static int categoryID = -1; //Getter returns Integer because it needs to be nullable at times
        private static String categoryName = "";
        private static String apiBaseURL;
        private static int currentQuestionIndex = 0;
        private static int lastQuestionIndex = 0;
        private static String correctAnswer;
        private static int amountOfQuestions = 10;
        private static String difficulty = null;//"any";
        private static String questionType = null;//"any";
        private static String sessionToken = null;

        public static String getSessionToken() {
            return sessionToken;
        }

        public static void setSessionToken(String sessionToken) {
            QuizConfig.sessionToken = sessionToken;
        }

        /**
         * When null, random questions will be supplied by the API
         * @return Integer Nullable
         */
        public static Integer getCategoryID()
        {
            if (categoryID == -1)
                return null;
            return categoryID;
        }

        public static void setCategoryID(int categoryID)
        {
            QuizConfig.categoryID = categoryID;
        }

        public static String getCategoryName()
        {
           return categoryName;
        }

        public static void setCategoryName(String categoryName)
        {
            QuizConfig.categoryName = categoryName;
        }

        public static String getApiBaseURL() {
            return apiBaseURL;
        }

        public static void setApiBaseURL(String apiBaseURL) {
            QuizConfig.apiBaseURL = apiBaseURL;
        }

        public static int getCurrentQuestionIndex() {
            return currentQuestionIndex;
        }

        public static void resetCurrentQuestionIndex() {
            currentQuestionIndex = 0;
        }

        public static void setNextQuestionIndex()
        {
            ++currentQuestionIndex;
        }

        public static int getLastQuestionIndex() {
            return lastQuestionIndex;
        }

        public static void setLastQuestionIndex(int index) {
            lastQuestionIndex = index;
        }

        public static String getCorrectAnswer() {
            return correctAnswer;
        }

        public static void setCorrectAnswer(String correctAnswer)
        {
            QuizConfig.correctAnswer = correctAnswer;
        }


        public static int getAmountOfQuestions() {
            return amountOfQuestions;
        }

        public static void setAmountOfQuestions(int amountOfQuestions) {
            QuizConfig.amountOfQuestions = amountOfQuestions;
        }

        public static String getDifficulty() {
            return difficulty;
        }

        public static void setDifficulty(String difficulty) {
            QuizConfig.difficulty = difficulty;
        }

        public static String getQuestionType() {
            return questionType;
        }

        public static void setQuestionType(String questionType) {
            QuizConfig.questionType = questionType;
        }
    }
}
