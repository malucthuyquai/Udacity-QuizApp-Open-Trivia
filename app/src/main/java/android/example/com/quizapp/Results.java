package android.example.com.quizapp;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

import com.itternet.QuizConfig;

import static android.R.id.message;

public class Results extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        // Get the Intent that started this activity and extract the string
        Intent intent = getIntent();
        Bundle extrasBundle = intent.getExtras();
        if(!extrasBundle.isEmpty())
        {
            // Capture the layout's TextView and set the string as its text
            TextView textView = (TextView) findViewById(R.id.resultsTextView);
            int score = extrasBundle.getInt("score");
            int questions = extrasBundle.getInt("questions");
            textView.setText(String.format(getResources().getString(R.string.scoreMessage), score, questions));
        }



    }
}