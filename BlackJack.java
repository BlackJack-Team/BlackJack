package BlackJack;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.*;

// Imports for database connectivity
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BlackJack {

    // Card class remains the same
    private class Card {
        String value;
        String type;

        Card(String value, String type) {
            this.value = value;
            this.type = type;
        }

        public String toString() {
            return value + "-" + type;
        }

        public int getValue() {
            if ("AJQK".contains(value)) { //A J Q K
                if (value.equals("A")) {
                    return 11;
                }
                return 10;
            }
            return Integer.parseInt(value); //2-10
        }

        public boolean isAce() {
            return value.equals("A");
        }

        public String getImagePath() {
            return "./cards/" + toString() + ".png";
        }
    }

    // Game variables
    ArrayList<Card> deck;
    Random random = new Random();

    // Dealer
    Card hiddenCard;
    ArrayList<Card> dealerHand;
    int dealerSum;
    int dealerAceCount;

    // Player
    ArrayList<Card> playerHand;
    int playerSum;
    int playerAceCount;

    // Player Info for Database
    String playerName;
    int playerId;

    // Window
    int boardWidth = 624;
    int boardHeight = boardWidth + 20; // Adjusted for button panel
    int cardWidth = 110;
    int cardHeight = 154;

    // Swing components
    JFrame frame = new JFrame("Black Jack");
    JPanel gamePanel = new JPanel() {
        public void centerString(Graphics g, Rectangle r, String s, Font font) {
            FontRenderContext frc = new FontRenderContext(null, true, true);

            Rectangle2D r2D = font.getStringBounds(s, frc);
            int rWidth = (int) Math.round(r2D.getWidth());
            int rHeight = (int) Math.round(r2D.getHeight());
            int rX = (int) Math.round(r2D.getX());
            int rY = (int) Math.round(r2D.getY());

            int a = (r.width / 2) - (rWidth / 2) - rX;
            int b = (r.height / 2) - (rHeight / 2) - rY;

            g.setFont(font);
            g.setColor(Color.white);
            g.drawString(s, r.x + a, r.y + b);
        }
        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            try {
                // Draw hidden card
                Image hiddenCardImg;
                if (gameEnded) {
                    hiddenCardImg = new ImageIcon(getClass().getResource(hiddenCard.getImagePath())).getImage();
                } else {
                    hiddenCardImg = new ImageIcon(getClass().getResource("./cards/BACK.png")).getImage();
                }
                g.drawImage(hiddenCardImg, 20, 20, cardWidth, cardHeight, null);

                // Draw dealer's other cards
                for (int i = 0; i < dealerHand.size(); i++) {
                    Card card = dealerHand.get(i);
                    Image cardImg = new ImageIcon(getClass().getResource(card.getImagePath())).getImage();
                    g.drawImage(cardImg, cardWidth + 25 + (cardWidth + 5) * i, 20, cardWidth, cardHeight, null);
                }

                // Draw player's cards
                for (int i = 0; i < playerHand.size(); i++) {
                    Card card = playerHand.get(i);
                    Image cardImg = new ImageIcon(getClass().getResource(card.getImagePath())).getImage();
                    g.drawImage(cardImg, 20 + (cardWidth + 5) * i, 396, cardWidth, cardHeight, null);
                }

                // Draw game over message
                if (gameEnded) {
                    dealerSum = reduceAce(dealerSum, dealerAceCount);
                    playerSum = reduceAce(playerSum, playerAceCount);

                    String message = "";
                    if (playerSum > 21) {
                        message = "You Bust! You Lose!";
                    } else if (dealerSum > 21) {
                        message = "Dealer Busts! You Win!";
                    } else if (playerSum == dealerSum) {
                        message = "Tie!";
                    } else if (playerSum > dealerSum) {
                        message = "You Win!";
                    } else {
                        message = "You Lose!";
                    }
                    Font font =  new Font("Arial", Font.BOLD, 40);
                    Rectangle rect = new Rectangle(0, 0, getWidth(), getHeight());
                    centerString(g, rect, message, font);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
    JPanel buttonPanel = new JPanel();
    JButton hitButton = new JButton("Hit");
    JButton stayButton = new JButton("Stand");
    JButton newGameButton = new JButton("New Game"); // Button to restart

    private boolean gameEnded = false; // Flag to check if game is over

    BlackJack() {
        // Get player name and set up database entry
        playerName = JOptionPane.showInputDialog(frame, "Enter your name:", "Black Jack", JOptionPane.PLAIN_MESSAGE);
        if (playerName == null || playerName.trim().isEmpty()) {
            System.exit(0); // Exit if user cancels or enters nothing
        }
        this.playerId = getOrCreatePlayerId(playerName);
        if (this.playerId == -1) {
            JOptionPane.showMessageDialog(frame, "Database connection failed. The application will now close.", "Database Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1); // Exit on DB error
        }

        frame.setVisible(true);
        frame.setSize(boardWidth, boardHeight);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        gamePanel.setLayout(new BorderLayout());
        gamePanel.setBackground(new Color(53, 101, 77)); // Dark green color
        frame.add(gamePanel);

        hitButton.setFocusable(false);
        buttonPanel.add(hitButton);
        stayButton.setFocusable(false);
        buttonPanel.add(stayButton);
        newGameButton.setFocusable(false);
        newGameButton.setEnabled(false); // Initially disabled
        buttonPanel.add(newGameButton);
        frame.add(buttonPanel, BorderLayout.SOUTH);

        hitButton.addActionListener(e -> {
            Card card = deck.remove(deck.size() - 1);
            playerSum += card.getValue();
            playerAceCount += card.isAce() ? 1 : 0;
            playerHand.add(card);

            if (reduceAce(playerSum, playerAceCount) > 21) { // Player busts
                endGame("Loss");
            }
            gamePanel.repaint();
        });

        stayButton.addActionListener(e -> {
            // Dealer's turn
            while (reduceAce(dealerSum, dealerAceCount) < 17) {
                Card card = deck.remove(deck.size() - 1);
                dealerSum += card.getValue();
                dealerAceCount += card.isAce() ? 1 : 0;
                dealerHand.add(card);
            }

            // Determine winner
            int finalDealerSum = reduceAce(dealerSum, dealerAceCount);
            int finalPlayerSum = reduceAce(playerSum, playerAceCount);
            String outcome;
            if (finalPlayerSum > 21 || (finalDealerSum <= 21 && finalPlayerSum < finalDealerSum)) {
                outcome = "Loss";
            } else if (finalDealerSum > 21 || finalPlayerSum > finalDealerSum) {
                outcome = "Win";
            } else {
                outcome = "Tie";
            }
            endGame(outcome);
        });

        newGameButton.addActionListener(e -> {
            startGame();
            gamePanel.repaint();
        });

        startGame();
    }

    public void startGame() {
        gameEnded = false;
        hitButton.setEnabled(true);
        stayButton.setEnabled(true);
        newGameButton.setEnabled(false);

        buildDeck();
        shuffleDeck();

        // Dealer's hand
        dealerHand = new ArrayList<Card>();
        dealerSum = 0;
        dealerAceCount = 0;

        hiddenCard = deck.remove(deck.size() - 1);
        dealerSum += hiddenCard.getValue();
        dealerAceCount += hiddenCard.isAce() ? 1 : 0;

        Card card = deck.remove(deck.size() - 1);
        dealerSum += card.getValue();
        dealerAceCount += card.isAce() ? 1 : 0;
        dealerHand.add(card);

        // Player's hand
        playerHand = new ArrayList<Card>();
        playerSum = 0;
        playerAceCount = 0;
        for (int i = 0; i < 2; i++) {
            card = deck.remove(deck.size() - 1);
            playerSum += card.getValue();
            playerAceCount += card.isAce() ? 1 : 0;
            playerHand.add(card);
        }
    }

    private void endGame(String outcome) {
        gameEnded = true;
        hitButton.setEnabled(false);
        stayButton.setEnabled(false);
        newGameButton.setEnabled(true);

        saveGameResult(outcome, reduceAce(playerSum, playerAceCount), reduceAce(dealerSum, dealerAceCount));
        gamePanel.repaint();
    }

    public void buildDeck() {
        deck = new ArrayList<Card>();
        String[] values = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
        String[] types = {"C", "D", "H", "S"};
        for (String type : types) {
            for (String value : values) {
                deck.add(new Card(value, type));
            }
        }
    }

    public void shuffleDeck() {
        for (int i = 0; i < deck.size(); i++) {
            int j = random.nextInt(deck.size());
            Card currCard = deck.get(i);
            Card randomCard = deck.get(j);
            deck.set(i, randomCard);
            deck.set(j, currCard);
        }
    }

    public int reduceAce(int sum, int aceCount) {
        while (sum > 21 && aceCount > 0) {
            sum -= 10;
            aceCount--;
        }
        return sum;
    }

    // --- DATABASE METHODS ---

    private Connection connect() throws SQLException {
        // !! IMPORTANT !!
        // Replace with your database URL, username, and password.
        String url = "jdbc:mysql://localhost:3306/blackjack_db";
        String user = "root";
        String password = "Jatin@890";
        return DriverManager.getConnection(url, user, password);
    }

    private int getOrCreatePlayerId(String playerName) {
        String selectSql = "SELECT player_id FROM players WHERE player_name = ?";
        String insertSql = "INSERT INTO players(player_name) VALUES(?)";
        try (Connection conn = connect()) {
            // Check if player exists
            PreparedStatement selectStmt = conn.prepareStatement(selectSql);
            selectStmt.setString(1, playerName);
            ResultSet rs = selectStmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("player_id"); // Player found
            } else {
                // Player not found, create new player
                PreparedStatement insertStmt = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS);
                insertStmt.setString(1, playerName);
                insertStmt.executeUpdate();
                ResultSet generatedKeys = insertStmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1); // Return the new ID
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return -1; // Indicate error
        }
        return -1;
    }

    private void saveGameResult(String outcome, int pScore, int dScore) {
        String sql = "INSERT INTO games(player_id, game_outcome, player_score, dealer_score) VALUES(?, ?, ?, ?)";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, this.playerId);
            pstmt.setString(2, outcome);
            pstmt.setInt(3, pScore);
            pstmt.setInt(4, dScore);
            pstmt.executeUpdate();
            System.out.println("Game result saved for player ID: " + this.playerId);
        } catch (SQLException e) {
            System.out.println("Failed to save game result.");
            e.printStackTrace();
        }
    }
}