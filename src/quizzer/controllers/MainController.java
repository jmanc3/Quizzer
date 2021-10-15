package quizzer.controllers;

import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainController implements Initializable {

    public HBox textFieldSectionHBox;
    public Button settingsButton;
    public TextField queryTextField; // We need to expose this field so that we can bind Ctrl+L
    public Button searchButton;
    public VBox foundTermsVBox;
    public Label oldInfoLabel;
    public TitledPane summaryInformationTab;
    public VBox summaryInformationVBox;
    public Label totalElapsedTimeLabel;
    public HBox titledPaneHBoxContainer;

    public SettingsController settingsController;
    public Parent settingsRoot;
    public StackPane stackpane;

    String previousClipboardChange = "";

    public void textDidChangeTo(String text) {
        if (!previousClipboardChange.equals(text)) {
            Platform.runLater(() ->
            {
                if (settingsController.copyDetectionCheck.isSelected()) {
                    queryTextField.setText(text);
                    previousClipboardChange = text;

                    if (settingsController.autoSearchCheck.isSelected()) {
                        try {
                            findTermsMatchingTheProvidedQuery();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        titledPaneHBoxContainer.setPadding(new Insets(0, 36, 0, 0));
        titledPaneHBoxContainer.minWidthProperty().bind(summaryInformationTab.widthProperty());

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.addFlavorListener(e ->
        {
            Clipboard clipboard1 = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard1.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                try {
                    String text = (String) clipboard1.getData(DataFlavor.stringFlavor);
                    textDidChangeTo(text);
                } catch (UnsupportedFlavorException | IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        Thread t = new Thread(new Runnable() {
            private Integer currentHashcode;

            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                    }
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    Transferable contents = clipboard.getContents(this);
                    if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        try {
                            String text = (String) clipboard.getData(DataFlavor.stringFlavor);
                            if (currentHashcode == null) {
                                currentHashcode = text.hashCode();
                            } else if (currentHashcode != text.hashCode()) {
                                currentHashcode = text.hashCode();
                                textDidChangeTo(text);
                            }
                        } catch (UnsupportedFlavorException | IOException ex) {
                            ex.printStackTrace();
                        }
                    } else {
                        currentHashcode = null;
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();

        queryTextField = new TextField() {
            @Override
            public void paste() {
                super.paste();
                if (settingsController.pasteCheck.isSelected()) {
                    try {
                        findTermsMatchingTheProvidedQuery();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        HBox.setHgrow(textFieldSectionHBox, Priority.ALWAYS);
        textFieldSectionHBox.getChildren().clear();
        HBox.setHgrow(queryTextField, Priority.ALWAYS);
        textFieldSectionHBox.getChildren().add(settingsButton);
        queryTextField.setPromptText("What's your question? (Ctrl+L)");
        textFieldSectionHBox.getChildren().add(queryTextField);
        textFieldSectionHBox.getChildren().add(searchButton);
        queryTextField.setOnAction(event ->
        {
            try {
                findTermsMatchingTheProvidedQuery();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private class Term {
        String left;
        String right;
        double similarity;
    }

    private class Set {
        String id;
        ArrayList<Term> terms = new ArrayList<>();
    }

    // So we only do one search at a time
    private AtomicBoolean tryingToFindMatchingTermsAlready = new AtomicBoolean(false);

    // We cache every set we download so we don't hit quizlet too hard
    private ArrayList<Set> cachedSets = new ArrayList<>();

    // This array of sets is filled during a search then cached and cleared at the end of it
    private ArrayList<Set> newestSets = new ArrayList<>();

    long searchStartTime = 0;

    public void findTermsMatchingTheProvidedQuery() throws InterruptedException {
        if (queryTextField.getText().trim().isEmpty()) return;

        if (tryingToFindMatchingTermsAlready.get()) return;
        tryingToFindMatchingTermsAlready.set(true);

        searchStartTime = System.currentTimeMillis();

        ScrollPane scrollPane = null;
        { // Cached Section
            if (!cachedSets.isEmpty()) {
                scrollPane = new ScrollPane();
                scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                scrollPane.getStyleClass().setAll("edge-to-edge");
                cachedSetsHBox.getChildren().clear();
                cachedSetsHBox.setSpacing(14);
                cachedSetsHBox.setFillHeight(true);

                for (Set cachedSet : cachedSets) {
                    AnchorPane cachedSetTaskBox = createTaskBox();
                    cachedSetTaskBox.setUserData(cachedSet);
                    cachedSetTaskBox.getStyleClass().setAll("taskbox-waiting");
                    ((Label) (cachedSetTaskBox.getChildren().get(0))).setText("Set " + cachedSet.id);
                    ((Label) (cachedSetTaskBox.getChildren().get(1))).setText("Waiting...");
                    cachedSetsHBox.getChildren().add(cachedSetTaskBox);
                }
                scrollPane.setContent(cachedSetsHBox);
            }
        }

        ScrollPane finalScrollPane = scrollPane;
        Platform.runLater(() -> {
            oldInfoLabel.setText("Searching...");
            oldInfoLabel.setVisible(true);
            totalElapsedTimeLabel.textProperty().bind(totalElapsedTimeProperty);
            foundTermsVBox.getChildren().clear();

            summaryInformationVBox.getChildren().clear();

            { // Google Section
                summaryInformationVBox.getChildren().add(new Label("Google Search"));
                googleTaskBox = createTaskBox();
                googleTaskBox.getStyleClass().setAll("taskbox-checking");
                ((Label) (googleTaskBox.getChildren().get(1))).textProperty().bind(googleTaskBoxProperty);
                summaryInformationVBox.getChildren().add(googleTaskBox);
            }

            if (finalScrollPane != null) {
                if (cachedSets.size() == 1) {
                    HBox titleAndSummaryInfoHBox = new HBox();
                    titleAndSummaryInfoHBox.getChildren().add(new Label("Cached Set"));
                    Region expandRegion = new Region();
                    HBox.setHgrow(expandRegion, Priority.ALWAYS);
                    titleAndSummaryInfoHBox.getChildren().add(expandRegion);
                    Label summaryLabel = new Label("");
                    summaryLabel.textProperty().bind(cachedSetsProperty);
                    titleAndSummaryInfoHBox.getChildren().add(summaryLabel);

                    summaryInformationVBox.getChildren().add(titleAndSummaryInfoHBox);
                } else {
                    HBox titleAndSummaryInfoHBox = new HBox();
                    titleAndSummaryInfoHBox.getChildren().add(new Label("Cached Set(s)"));
                    Region expandRegion = new Region();
                    HBox.setHgrow(expandRegion, Priority.ALWAYS);
                    titleAndSummaryInfoHBox.getChildren().add(expandRegion);
                    Label summaryLabel = new Label("");
                    summaryLabel.textProperty().bind(cachedSetsProperty);
                    titleAndSummaryInfoHBox.getChildren().add(summaryLabel);

                    summaryInformationVBox.getChildren().add(titleAndSummaryInfoHBox);
                }
                summaryInformationVBox.getChildren().add(finalScrollPane);
            }

            summaryInformationTab.setExpanded(true);
            summaryInformationTab.setDisable(false);
        });

        Task<Boolean> workLauncherTask = new Task<Boolean>() {
            @Override
            protected Boolean call() throws InterruptedException {
                httpErrors.set(false);

                Thread googleSearchThread = new Thread(searchForMatchInFreshTopGoogleResults());
                googleSearchThread.setDaemon(true);
                googleSearchThread.start();

                Task<Boolean> cachedSearchTask = searchForMatchInAllCachedSets();
                Thread cachedSearchThread = new Thread(cachedSearchTask);
                cachedSearchThread.setDaemon(true);
                cachedSearchThread.start();

                Task<Boolean> updateTimeStatisticsTask = updateTimeStatistics();
                Thread timeStatisticsThread = new Thread(updateTimeStatisticsTask);
                timeStatisticsThread.setDaemon(true);
                timeStatisticsThread.start();

                googleSearchThread.join();
                cachedSearchThread.join();
                updateTimeStatisticsTask.cancel();
                timeStatisticsThread.join();

                Platform.runLater(() -> summaryInformationTab.setExpanded(false));

                // Cache new sets if not cached
                for (Set newSet : newestSets)
                    if (!cachedSets.contains(newSet))
                        cachedSets.add(newSet);
                newestSets.clear();

                tryingToFindMatchingTermsAlready.set(false);

                Platform.runLater(() -> {
                    if (settingsController.clearCheck.isPressed()) {
                        queryTextField.setText("");
                    }
                    oldInfoLabel.setVisible(false);
                });
                return null;
            }
        };

        Thread workThread = new Thread(workLauncherTask);
        workThread.setDaemon(true);
        workThread.start();
    }

    private SimpleStringProperty googleTaskBoxProperty = new SimpleStringProperty("Elapsed Time: 0ms");
    private SimpleStringProperty cachedSetsProperty = new SimpleStringProperty("Elapsed Time 0ms, Checked 0/0, Matches 0/0");
    private SimpleStringProperty topSetsProperty = new SimpleStringProperty("Elapsed Time 0ms, Checked 0/0, Matches 0/0");
    private SimpleStringProperty totalElapsedTimeProperty = new SimpleStringProperty("Total Elapsed Time: 0ms");
    private AnchorPane googleTaskBox = null;
    private HBox cachedSetsHBox = new HBox();

    class PossibleSet {
        AnchorPane taskbox;
        String link;
        String id;

        public PossibleSet(String link, String id) {
            this.link = link;
            this.id = id;
        }
    }

    private void downloadAndCheckSet(PossibleSet possibleSet, String rawQuery) {
        {
            Platform.runLater(() -> {
                possibleSet.taskbox.getStyleClass().setAll("taskbox-checking");
                ((Label) (possibleSet.taskbox.getChildren().get(1))).setText("Checking...");
            });
            Document page = null;
            Connection.Response setResponse = null;
            try {
                setResponse = Jsoup.connect(possibleSet.link)
                        .userAgent("Mozilla")
                        .cookie("auth", "token")
                        .timeout(20000)
                        .execute();
                page = setResponse.parse();
            } catch (Exception e) {
                e.printStackTrace();
                String errorMessage = "Http Error Code: " + setResponse.statusCode();
                Platform.runLater(() -> {
                    possibleSet.taskbox.getStyleClass().setAll("taskbox-failed");
                    ((Label) (possibleSet.taskbox.getChildren().get(1))).setText(errorMessage);
                });
                httpErrors.set(true);
                return;
            }
            Elements terms = page.select("span[class='TermText notranslate lang-en']");
            Set set = new Set();
            set.id = possibleSet.id;

            for (int i = 0; i < terms.size(); i += 2) {
                Element questionElement = terms.get(i);
                Element answerElement = terms.get(i + 1);

                // Join the text and clean up some of the html stuff
                String questionHtml = questionElement.childNodes().stream().map(Node::toString).collect(Collectors.joining());
                String answerHtml = answerElement.childNodes().stream().map(Node::toString).collect(Collectors.joining());

                questionHtml = questionHtml.replaceAll("<br>", "\n");
                answerHtml = answerHtml.replaceAll("<br>", "\n");

                // The more accurate cleaning would use the code below, but that Jsoup parser gets rid
                // of new lines which we need so we don't use it for now. I'll look at it later.
//                                String questionText = Jsoup.parse(questionHtml).text();
//                                String answerText = Jsoup.parse(answerHtml).text();

                Term term = new Term();
                term.left = questionHtml;
                term.right = answerHtml;
                set.terms.add(term);
            }

            // Add this set to our list of new sets, which will be moved to cache later
            newestSets.add(set);

            AtomicBoolean foundAny = new AtomicBoolean(false);
            NormalizedLevenshtein l = new NormalizedLevenshtein();
            // For each new set, find the most similar term, and push it to the screen if it meets the minimum score
            set.terms.parallelStream().filter(term -> {
                double matchSimilarity = l.similarity(term.left, rawQuery);
                double descriptionSimilarity = l.similarity(term.right, rawQuery);

                if (matchSimilarity > .7) {
                    term.similarity = matchSimilarity;
                    return true;
                } else if (descriptionSimilarity > .7) {
                    String temp = term.left;
                    term.left = term.right;
                    term.right = temp;
                    term.similarity = descriptionSimilarity;
                    return true;
                }

                return false;
            }).sorted(Comparator.comparingDouble(value -> -value.similarity)).forEach(term -> {
                foundAny.set(true);
                Platform.runLater(() -> {
                    addOption(term);
                });
            });
            if (foundAny.get()) {
                Platform.runLater(() -> {
                    possibleSet.taskbox.getStyleClass().setAll("taskbox-matched");
                    ((Label) (possibleSet.taskbox.getChildren().get(1))).setText("Matched");
                });
            } else {
                Platform.runLater(() -> {
                    possibleSet.taskbox.getStyleClass().setAll("taskbox-failed");
                    ((Label) (possibleSet.taskbox.getChildren().get(1))).setText("Failed");
                });
            }
        }
    }

    private Task<Boolean> searchForMatchInFreshTopGoogleResults() {
        return new Task<Boolean>() {
            @Override
            protected Boolean call() {
                String rawQuery = queryTextField.getText();

                googleSearchStartTime.set(System.currentTimeMillis());
                googleSearchEndTime.set(-1);
                // Get response from Google
                Document googleResponseDocument = null;
                Connection.Response googleResponse = null;
                try {
                    Platform.runLater(() -> googleTaskBox.getStyleClass().setAll("taskbox-checking"));
                    googleResponse = Jsoup.connect("https://google.com/search?q=\"" + rawQuery.replace(" ", "%20") + "\"%20quizlet")
                            .userAgent("Mozilla")
                            .cookie("auth", "token")
                            .timeout(20000)
                            .execute();
                    googleResponseDocument = googleResponse.parse();
                } catch (IOException e) {
                    e.printStackTrace();
                    String errorMessage = "Http Error Code: " + googleResponse.statusCode();
                    googleSearchEndTime.set(System.currentTimeMillis());
                    Platform.runLater(() -> {
                        googleTaskBox.getStyleClass().setAll("taskbox-failed");
                        googleTaskBoxProperty.set(errorMessage);
                    });
                    httpErrors.set(true);
                    return null;
                }
                Platform.runLater(() -> googleTaskBox.getStyleClass().setAll("taskbox-matched"));
                googleSearchEndTime.set(System.currentTimeMillis());


                // Search through that response for set ID's
                ArrayList<PossibleSet> rawPossibleSets = new ArrayList<>();
                Elements links = googleResponseDocument.select("a[href]");
                for (Element link : links) {
                    if (link.attr("abs:href").startsWith("https://www.google.com/url?q=https://quizlet.com")) {
                        String linkText = link.attr("abs:href").substring(29);

                        Pattern pattern = Pattern.compile("quizlet\\.com\\/[0-9].*?\\/");
                        Matcher matcher = pattern.matcher(linkText);

                        while (matcher.find()) {
                            String group = matcher.group();
                            String id = group.substring(12, group.length() - 1);
                            rawPossibleSets.add(new PossibleSet(linkText, id));
                        }
                    }
                }
                // Remove any id's which are already in our cache
                List<PossibleSet> possibleSets = rawPossibleSets.stream().filter(possibleSet -> {
                    Optional<Set> alreadyInCache = cachedSets.stream().filter(cachedSet -> cachedSet.id.equals(possibleSet.id)).findAny();
                    return !alreadyInCache.isPresent();
                }).collect(Collectors.toList());

                if (!possibleSets.isEmpty()) {
                    FlowPane flowPane = new FlowPane();
                    flowPane.setVgap(14);
                    flowPane.setHgap(14);

                    Platform.runLater(() -> {
                        if (possibleSets.size() == 1) {
                            HBox titleAndSummaryInfoHBox = new HBox();
                            titleAndSummaryInfoHBox.getChildren().add(new Label("Top Set"));
                            Region expandRegion = new Region();
                            HBox.setHgrow(expandRegion, Priority.ALWAYS);
                            titleAndSummaryInfoHBox.getChildren().add(expandRegion);
                            Label summaryLabel = new Label("");
                            summaryLabel.textProperty().bind(topSetsProperty);
                            titleAndSummaryInfoHBox.getChildren().add(summaryLabel);
                            summaryInformationVBox.getChildren().add(titleAndSummaryInfoHBox);
                        } else {
                            HBox titleAndSummaryInfoHBox = new HBox();
                            titleAndSummaryInfoHBox.getChildren().add(new Label("Top Set(s)"));
                            Region expandRegion = new Region();
                            HBox.setHgrow(expandRegion, Priority.ALWAYS);
                            titleAndSummaryInfoHBox.getChildren().add(expandRegion);
                            Label summaryLabel = new Label("");
                            summaryLabel.textProperty().bind(topSetsProperty);
                            titleAndSummaryInfoHBox.getChildren().add(summaryLabel);
                            summaryInformationVBox.getChildren().add(titleAndSummaryInfoHBox);
                        }
                        for (PossibleSet possibleSet : possibleSets) {
                            possibleSet.taskbox = createTaskBox();
                            possibleSet.taskbox.getStyleClass().setAll("taskbox-waiting");
                            ((Label) (possibleSet.taskbox.getChildren().get(0))).setText("Set " + possibleSet.id);
                            ((Label) (possibleSet.taskbox.getChildren().get(1))).setText("Waiting...");
                            flowPane.getChildren().add(possibleSet.taskbox);
                        }
                        summaryInformationVBox.getChildren().add(flowPane);
                    });
                }

                // Go through each possible set, download it, and then check it.
                // The reason we don't use parallel stream here is because Quizlet will get mad if we try to download that
                // many sets at the same time.
                // This used to be a non issue when they provided their API where you could get multiple sets in one query
                // but they canned it. -_-

                freshSearchStartTime.set(System.currentTimeMillis());
                freshSearchEndTime.set(-1);

                if (settingsController.sequentialCheck.isSelected()) {
                    possibleSets.forEach(possibleSet -> downloadAndCheckSet(possibleSet, rawQuery));
                } else {
                    possibleSets.parallelStream().forEach(possibleSet -> downloadAndCheckSet(possibleSet, rawQuery));
                }

                freshSearchEndTime.set(System.currentTimeMillis());

                return null;
            }
        };
    }

    private Task<Boolean> searchForMatchInAllCachedSets() {
        return new Task<Boolean>() {
            @Override
            protected Boolean call() {
                String rawQuery = queryTextField.getText();

                cachedSearchStartTime.set(System.currentTimeMillis());
                cachedSearchEndTime.set(-1);
                cachedSetsHBox.getChildren().parallelStream().forEach(node -> {
                    AnchorPane taskBox = (AnchorPane) node;

                    Set cachedSet = (Set) taskBox.getUserData();

                    AtomicBoolean foundAny = new AtomicBoolean(false);
                    NormalizedLevenshtein l = new NormalizedLevenshtein();
                    // For each new set, find the most similar term, and push it to the screen if it meets the minimum score
                    cachedSet.terms.parallelStream().filter(term -> {
                        double matchSimilarity = l.similarity(term.left, rawQuery);
                        double descriptionSimilarity = l.similarity(term.right, rawQuery);

                        if (matchSimilarity > .7) {
                            term.similarity = matchSimilarity;
                            return true;
                        } else if (descriptionSimilarity > .7) {
                            String temp = term.left;
                            term.left = term.right;
                            term.right = temp;
                            term.similarity = descriptionSimilarity;
                            return true;
                        }

                        return false;
                    }).sorted(Comparator.comparingDouble(value -> -value.similarity)).forEach(term -> {
                        foundAny.set(true);
                        Platform.runLater(() -> {
                            addOption(term);
                        });
                    });
                    if (foundAny.get()) {
                        Platform.runLater(() -> {
                            taskBox.getStyleClass().setAll("taskbox-matched");
                            ((Label) (taskBox.getChildren().get(1))).setText("Matched");
                        });
                    } else {
                        Platform.runLater(() -> {
                            taskBox.getStyleClass().setAll("taskbox-failed");
                            ((Label) (taskBox.getChildren().get(1))).setText("Failed");
                        });
                    }
                });

                cachedSearchEndTime.set(System.currentTimeMillis());
                return null;
            }
        };
    }

    AtomicBoolean httpErrors = new AtomicBoolean(false);

    AtomicLong googleSearchStartTime = new AtomicLong(0);
    AtomicLong googleSearchEndTime = new AtomicLong(0);

    AtomicLong cachedSearchStartTime = new AtomicLong(0);
    AtomicLong cachedSearchEndTime = new AtomicLong(0);
    AtomicLong freshSearchStartTime = new AtomicLong(0);
    AtomicLong freshSearchEndTime = new AtomicLong(0);

    private Task<Boolean> updateTimeStatistics() {
        return new Task<Boolean>() {
            @Override
            protected Boolean call() throws InterruptedException {
                while (!isCancelled()) {
                    long currentTime = System.currentTimeMillis();
                    long googleStartTime = googleSearchStartTime.get();
                    long googleEndTime = googleSearchEndTime.get();
                    long freshStartTime = freshSearchStartTime.get();
                    long freshEndTime = freshSearchEndTime.get();
                    long cachedStartTime = cachedSearchStartTime.get();
                    long cachedEndTime = cachedSearchEndTime.get();

                    Platform.runLater(() -> {
                        String prefix = httpErrors.get() ? "<HTTP errors, non complete results> " : "";

                        totalElapsedTimeProperty.set(prefix + "Total Elapsed Time: " + (currentTime - searchStartTime) + "ms");
                        if (googleEndTime == -1) {
                            String googleValue = "Elapsed Time: " + (currentTime - googleStartTime) + "ms";
                            googleTaskBoxProperty.set(googleValue);
                        } else {
                            String googleValue = "Elapsed Time: " + (googleEndTime - googleStartTime) + "ms";
                            googleTaskBoxProperty.set(googleValue);
                        }
                        if (freshEndTime == -1) {
                            String freshValue = "Elapsed Time: " + (currentTime - freshStartTime) + "ms";
                            topSetsProperty.set(freshValue);
                        } else {
                            String freshValue = "Elapsed Time: " + (freshEndTime - freshStartTime) + "ms";
                            topSetsProperty.set(freshValue);
                        }
                        if (cachedEndTime == -1) {
                            String cachedValue = "Elapsed Time: " + (currentTime - cachedStartTime) + "ms";
                            cachedSetsProperty.set(cachedValue);
                        } else {
                            String cachedValue = "Elapsed Time: " + (cachedEndTime - cachedStartTime) + "ms";
                            cachedSetsProperty.set(cachedValue);
                        }
                    });
                    Thread.sleep(16);
                }

                return null;
            }
        };
    }

    private void addOption(Term term) {
        oldInfoLabel.setVisible(false);

        VBox optionVBox = new VBox();

        GridPane gridPane = new GridPane();
        gridPane.getColumnConstraints().setAll(
                ColumnConstraintsBuilder.create().percentWidth(40).build(),
                ColumnConstraintsBuilder.create().percentWidth(1).build(),
                ColumnConstraintsBuilder.create().percentWidth(59).build()
        );
        gridPane.getRowConstraints().add(new RowConstraints(Control.USE_COMPUTED_SIZE));
        gridPane.setVgap(10);
        gridPane.setHgap(10);
        gridPane.setPadding(new Insets(0));
        gridPane.prefHeight(Control.USE_COMPUTED_SIZE);

        Label questionLabel = new Label();
        Label answerLabel = new Label();
        questionLabel.setText(term.left);
        answerLabel.setText(term.right);
        questionLabel.setPadding(new Insets(10));

        questionLabel.setWrapText(true);
        questionLabel.setPrefWidth(Control.USE_COMPUTED_SIZE);

        AnchorPane anchorPane = new AnchorPane();

        VBox questionVBox = new VBox();
        AnchorPane.setLeftAnchor(questionVBox, 0.0);
        AnchorPane.setTopAnchor(questionVBox, 0.0);
        AnchorPane.setRightAnchor(questionVBox, 0.0);
        AnchorPane.setBottomAnchor(questionVBox, 0.0);
        anchorPane.getChildren().add(questionVBox);

        questionVBox.getChildren().add(questionLabel);

        gridPane.addColumn(0, questionVBox);

//        Separator separator = new Separator();
//        separator.setOrientation(Orientation.VERTICAL);
//        gridPane.addColumn(1, separator);

        answerLabel.setWrapText(true);
        answerLabel.setPrefWidth(Control.USE_COMPUTED_SIZE);
        answerLabel.setPadding(new Insets(10));
        gridPane.addColumn(2, answerLabel);

        gridPane.prefHeight(Control.USE_COMPUTED_SIZE);
        gridPane.minHeight(Control.USE_COMPUTED_SIZE);

        gridPane.setUserData(answerLabel.getText());

        optionVBox.getChildren().add(gridPane);

//        Label similarityLabel = new Label(String.format("Similarity %d.2%%", Math.round(similarity * 100)));

        if (term.similarity == 1) {
            VBox match = new VBox();
            match.setPrefHeight(gridPane.getHeight());
            match.setPrefWidth(3);
            match.setMinWidth(3);

            match.getStyleClass().add("perfect-match");
            optionVBox.getChildren().add(match);
            foundTermsVBox.getChildren().add(0, optionVBox);
        } else {
            foundTermsVBox.getChildren().add(optionVBox);
        }

//        match.getChildren().add(similarityLabel);


        // TODO this should give a little notification telling the user what happend
        gridPane.setOnMouseClicked(event ->
        {
            String text = (String) gridPane.getUserData();
            previousClipboardChange = text;
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
        });
    }

    private AnchorPane createTaskBox() {
        AnchorPane node = new AnchorPane();
        node.setMinSize(175, 100);
        node.setPrefSize(175, 100);
        node.setMaxSize(175, 100);

        Label topLeftLabel = new Label();
        AnchorPane.setTopAnchor(topLeftLabel, 10d);
        AnchorPane.setLeftAnchor(topLeftLabel, 10d);
        Label centerLabel = new Label();
        AnchorPane.setTopAnchor(centerLabel, 0d);
        AnchorPane.setLeftAnchor(centerLabel, 0d);
        AnchorPane.setBottomAnchor(centerLabel, 0d);
        AnchorPane.setRightAnchor(centerLabel, 0d);
        node.getChildren().add(topLeftLabel);
        node.getChildren().add(centerLabel);
        centerLabel.setAlignment(Pos.CENTER);

        return node;
    }


    public void openSettings() {
        Platform.runLater(() -> {
            for (javafx.scene.Node child : stackpane.getChildren()) {
                child.setVisible(false);
            }
            stackpane.setAlignment(Pos.TOP_LEFT);
            settingsRoot.setVisible(true);
            settingsButton.setText("Close");
        });
    }

    public void closeSettings() {
        Platform.runLater(() -> {
            for (javafx.scene.Node child : stackpane.getChildren()) {
                child.setVisible(true);
            }
            stackpane.setAlignment(Pos.CENTER);
            settingsRoot.setVisible(false);
            settingsButton.setText("Settings");
        });
    }

    public void toggleSettings() {
        if (settingsRoot.isVisible()) {
            closeSettings();
        } else {
            openSettings();
        }
    }

}
