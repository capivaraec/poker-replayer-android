package com.capivaraec.pokerreplayer.history;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

import com.capivaraec.pokerreplayer.enums.ActionID;
import com.capivaraec.pokerreplayer.enums.Limit;
import com.capivaraec.pokerreplayer.enums.Session;
import com.capivaraec.pokerreplayer.enums.Street;

public class HistoryReader {

	private static final String BOARD = "Board";
	private static final String ALL_IN = " and is all-in";
	private static float pot = 0;
    private static float totalToCall = 0;

	public static History readFile(File file) {

		History history = new History();
		BufferedReader br = null;

		try {
			br = new BufferedReader(new FileReader(file));

			String line;

			Hand hand = null;
			HashMap<String, Player> players = null;
			Street street = Street.PRE_FLOP;
			while ((line = br.readLine()) != null) {
				if (line.contains("Level ")) {

					if (hand != null) {
						history.addHand(hand);
					}

					hand = new Hand();
					players = new HashMap<>();
					street = Street.PRE_FLOP;
					pot = 0;
                    totalToCall = 0;
					newHand(hand, players, line, history);
				} else if (line.startsWith("Table")) {
                    setButton(hand, line);
                    setNumPlayers(hand, line);
					setTable(hand, line);
				} else if (line.contains(" in chips)")) {
					setChips(hand, players, line);
				} else if (line.contains(" posts")) {
					setBlinds(hand, line);
				} else if (line.startsWith("Dealt to ")) {
					setCards(hand, players, line);
				} else if (line.contains("calls")) {
					setCall(hand, line, street);
				} else if (line.contains("bets") || line.contains("raises")) {
					setBetOrRaise(hand, line, street);
				} else if (line.contains("checks") || line.contains("folds")) {
					setCheckOrFold(hand, line, street);
				} else if (line.contains("Uncalled bet")) {
					setUncalledBet(hand, line, street);
				} else if (line.contains("*** FLOP ***")) {
					street = Street.FLOP;
                    totalToCall = 0;
                    assert players != null;
                    Player board = players.get(BOARD).clonePlayer();

					String[] card = getCards(line);

					board.addCard(card[0]);
					board.addCard(card[1]);
					board.addCard(card[2]);

					Action action = new Action(board, ActionID.FLOP, street, pot);
                    hand.addAction(action);
				} else if (line.contains("*** TURN ***")) {
					street = Street.TURN;
                    totalToCall = 0;
                    assert players != null;
                    Player board = players.get(BOARD).clonePlayer();

					String[] card = getCards(line);

					board.addCard(card[0]);

					Action action = new Action(board, ActionID.TURN, street, pot);
                    hand.addAction(action);
				} else if (line.contains("*** RIVER ***")) {
					street = Street.RIVER;
                    totalToCall = 0;
                    assert players != null;
                    Player board = players.get(BOARD).clonePlayer();
					String[] card = getCards(line);

					board.addCard(card[0]);

					Action action = new Action(board, ActionID.RIVER, street, pot);
                    hand.addAction(action);
				} else if (line.contains("shows")) {
					showdown(hand, line, street);
				} else if (line.contains("collected") && !line.contains("collected (")) {
					getPot(hand, line, street);
				}
				// TODO: sitting out e return
			}

			if (hand != null) {
				history.addHand(hand);
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();

			return null;
		} finally {
			try {
                assert br != null;
                br.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return history;
	}

	private static void newHand(Hand hand, HashMap<String, Player> players, String line, History history) {
		//TODO: colocar este método na classe PokerStarsReader
		Player player = new Player(BOARD);
		players.put(player.getName(), player);

        setSmallAndBigBlind(hand, line);

        setRoom(history, line);
        setSession(history, line);

		setNumHand(hand, line);
		setLimit(hand, line);
        setGame(hand, line);

        setTourney(history, line);

		setDate(hand, line);

		setBuyIn(history, hand, line);
	}

    private static void setSession(History history, String line) {
        if (history.getSession() == null) {
            if (line.contains("Tournament")) {
                history.setSession(Session.TOURNEY);
            } else {
                history.setSession(Session.RING);
            }
        }
    }

    private static void setBuyIn(History history, Hand hand, String line) {
        if (history.getBuyIn() == null) {
            int startIndex = line.indexOf(",") + 1;
            int endIndex = line.indexOf(hand.getGame());

            history.setBuyIn(line.substring(startIndex, endIndex).trim());
        }
    }

    private static void setDate(Hand hand, String line) {
        int startIndexDate = line.indexOf('[') + 1;
        int endIndexDate = line.trim().lastIndexOf(' ');
        String data = line.substring(startIndexDate, endIndexDate);

        Date date = formatDate(data);
        hand.setDate(date);
    }

    private static void setTourney(History history, String line) {
        if (history.getTourney() == 0) {
            int startIndexTourney = line.lastIndexOf('#') + 1;
            int endIndexTourney = line.indexOf(',');
            long tourney = Long.parseLong(line.substring(startIndexTourney, endIndexTourney));
            history.setTourney(tourney);
        }
    }

    private static void setLimit(Hand hand, String line) {
        if (line.contains("No Limit")) {
            hand.setLimit(Limit.NO_LIMIT);
        } else if (line.contains("Pot Limit")) {
            hand.setLimit(Limit.POT_LIMIT);
        } else if (line.contains("Limit")) {
            hand.setLimit(Limit.LIMIT);
        }
    }

    private static void setGame(Hand hand, String line) {
        int startIndex = line.indexOf("Hold'em");

        if (startIndex < 0) {
            startIndex = line.indexOf("Omaha");
        }

        String strLimit = null;
        switch (hand.getLimit()) {
            case NO_LIMIT:
                strLimit = "No Limit";
                break;
            case LIMIT:
                strLimit = "Limit";
                break;
            case POT_LIMIT:
                strLimit = "Pot Limit";
                break;
        }

        int endIndex = line.indexOf(strLimit);
        hand.setGame(line.substring(startIndex, endIndex).trim());
    }

    private static void setNumHand(Hand hand, String line) {
        int startHandIndex = line.indexOf('#') + 1;
        int endHandIndex = line.indexOf(':');
        long numHand = Long.parseLong(line.substring(startHandIndex, endHandIndex));
        hand.setHand(numHand);
    }

    private static void setSmallAndBigBlind(Hand hand, String line) {
        int leftParenthesis = line.indexOf('(');
        int rightParenthesis = line.indexOf(')');
        int slash = line.indexOf('/');

        int smallBlind = Integer.parseInt(line.substring(leftParenthesis + 1, slash));
        int bigBlind = Integer.parseInt(line.substring(slash + 1, rightParenthesis));

        hand.setSmallBlind(smallBlind);
        hand.setBigBlind(bigBlind);
    }

    private static void setRoom(History history, String line) {
        if (history.getRoom() == null) {
            int roomIndex = line.indexOf(" Hand");
            String room = line.substring(0, roomIndex);
            history.setRoom(room);
        }
    }

	private static Date formatDate(String data) {
		Date date;
		try {
			Locale locale = new Locale("en", "US");
			DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", locale);
			formatter.setTimeZone(TimeZone.getTimeZone("GMT-5"));
			date = formatter.parse(data);
		} catch (ParseException e) {
			return null;
		}
		return date;
	}

	private static void setTable(Hand hand, String line) {
		int initialIndexTable = line.indexOf('\'') + 1;
		int finalIndexTable = line.lastIndexOf('\'');
		String table = line.substring(initialIndexTable, finalIndexTable);

		hand.setTable(table);
	}

    private static void setNumPlayers(Hand hand, String line) {
        int finalIndexPlayers = line.indexOf("-max");
        int initialIndexPlayers = line.substring(0, finalIndexPlayers).lastIndexOf(' ') + 1;

        int numPlayers = Integer.parseInt(line.substring(initialIndexPlayers, finalIndexPlayers));

        hand.setNumPlayers(numPlayers);
    }

    private static void setButton(Hand hand, String line) {
        int initialIndexButton = line.indexOf("Seat #") + 6;
        int finalIndexButton = line.indexOf(" is the button");
        int button = Integer.parseInt(line.substring(initialIndexButton, finalIndexButton));
        hand.setButton(button);
    }

	private static void setChips(Hand hand, HashMap<String, Player> players, String line) {
		int indexColon = line.indexOf(':');

		int position = Integer.parseInt(line.substring(5, indexColon));

		int finalIndex = line.lastIndexOf(" in chips)");
		int leftParenthesis = line.substring(0, finalIndex).lastIndexOf("($");//TODO: verificar outras moedas

        if (leftParenthesis < 0) {
            leftParenthesis = line.substring(0, finalIndex).lastIndexOf('(');
        }

		float stack = Float.parseFloat(line.substring(leftParenthesis + 1, finalIndex));

		String name = line.substring(indexColon + 2, leftParenthesis - 1);

		Player player = new Player(name, stack, position);
		players.put(name, player);

		Player initialPlayer = new Player(name, stack, position);
		HashMap<String, Player> initialPlayers = hand.getPlayers();
		if (initialPlayers == null) {
			initialPlayers = new HashMap<>();
		}
		initialPlayers.put(name, initialPlayer);

		hand.setPlayers(initialPlayers);
	}

	private static void setBlinds(Hand hand, String line) {
		float value = getValue(line);

		ActionID actionID;
		Street street = Street.PRE_FLOP;

		if (line.contains(" small blind ")) {
			actionID = ActionID.SMALL_BLIND;
		} else if (line.contains(" big blind ")) {
			actionID = ActionID.BIG_BLIND;
            totalToCall = value;
		} else {
			actionID = ActionID.ANTE;
			street = Street.ANTE;
			hand.setAnte(value);
		}

        Player player = getClonedPlayer(line, hand.getPlayers(), value);

		pot += value;

		if (line.endsWith(ALL_IN)) {
			actionID = ActionID.ALL_IN;
		}

		Action action = new Action(player, value, actionID, street, pot, totalToCall, 0);
		hand.addAction(action);
	}

	private static void setCards(Hand hand, HashMap<String, Player> players, String line) {
		int finalIndex = line.indexOf('[') - 1;

		String[] cards = getCards(line);

		String name = line.substring(9, finalIndex);

		Player player = players.get(name).clonePlayer();
        for (String card : cards) {
            player.addCard(card);
        }

		Action action = new Action(player, ActionID.HOLE_CARDS, Street.PRE_FLOP, pot, totalToCall);
		hand.addAction(action);
	}

	private static void setBetOrRaise(Hand hand, String line, Street street) {
		float totalValue = getValue(line);
		String name = getPlayerName(line);
		float streetValue = getValueFromStreet(name, street, hand.getActions());
		float value = totalValue - streetValue;
        totalToCall = totalValue;

		setBetCallOrRaise(hand, hand.getPlayers(), line, street, value, totalValue, streetValue);
	}

	private static void setBetCallOrRaise(Hand hand, HashMap<String, Player> players, String line, Street street, float value, float totalValue, float streetValue) {
		Player player = getClonedPlayer(line, players, value);
		ActionID actionID = ActionID.CALL;

        if (line.contains("bets")) {
			actionID = ActionID.BET;
            if (line.contains(ALL_IN)) {
                actionID = ActionID.BET_ALL_IN;
            }
        } else if (line.contains("raises")) {
			actionID = ActionID.RAISE;
            if (line.contains(ALL_IN)) {
                actionID = ActionID.RAISE_ALL_IN;
            }
        } else if (line.contains(ALL_IN)) {
            actionID = ActionID.CALL_ALL_IN;
        }
		pot += value;
		Action action = new Action(player, totalValue, actionID, street, pot, totalToCall, streetValue);
		hand.addAction(action);
	}

	private static void setCall(Hand hand, String line, Street street) {
		float value = getValue(line);
		String name = getPlayerName(line);
		ArrayList<Action> actions = hand.getActions();
		float streetValue = getValueFromStreet(name, street, actions);
		float totalValue = value + streetValue;
		setBetCallOrRaise(hand, hand.getPlayers(), line, street, value, totalValue, streetValue);
	}

	private static void setCheckOrFold(Hand hand, String line, Street street) {
		Player player = getClonedPlayer(line, hand.getPlayers(), 0);
		ActionID actionID = ActionID.CHECK;

		if (line.contains("folds")) {
			actionID = ActionID.FOLD;
		}

        float streetValue = getValueFromStreet(player.getName(), street, hand.getActions());

		Action action = new Action(player, 0, actionID, street, pot, totalToCall, streetValue);
		hand.addAction(action);
	}

	private static void setUncalledBet(Hand hand, String line, Street street) {
		int leftParenthesis = line.indexOf('(') + 1;
		int rightParenthesis = line.indexOf(')');
		int value = Integer.parseInt(line.substring(leftParenthesis, rightParenthesis));

		int indexPlayer = line.indexOf("returned to") + 11;
		String name = line.substring(indexPlayer).trim();
		Player player = hand.getPlayers().get(name);
		player.setStack(player.getStack() + value);

		ActionID actionID = ActionID.UNCALLED_BET;
		pot -= value;

		float streetValue = getValueFromStreet(player.getName(), street, hand.getActions());

		Action action = new Action(player.clonePlayer(), value, actionID, street, pot, 0, streetValue);
		hand.addAction(action);
	}

	private static void getPot(Hand hand, String line, Street street) {
		int index = line.lastIndexOf(" collected");

		String name = line.substring(0, index);
		Player player = hand.getPlayers().get(name);

		int finalIndex = line.lastIndexOf(" from pot");

		if (finalIndex == -1) {
			finalIndex = line.lastIndexOf(" from side pot");
			if (finalIndex == -1) {
				finalIndex = line.lastIndexOf(" from main pot");
			}
		}

		int value = Integer.parseInt(line.substring(index + 11, finalIndex));
		player.setStack(player.getStack() + value);

		ActionID actionID = ActionID.COLLECTED;

		Action action = new Action(player.clonePlayer(), value, actionID, street, pot, 0, 0);
		hand.addAction(action);
	}

	private static void showdown(Hand hand, String line, Street street) {
		Player player = getClonedPlayer(line, hand.getPlayers(), 0);
		String[] cards = getCards(line);

        for (String card : cards) {
            player.addCard(card);
        }

		ActionID actionID = ActionID.SHOWDOWN;
		Action action = new Action( player, actionID, street, pot);

		for (int x = hand.getActions().size() - 1; x >= 0; x--) {
			Action act = hand.getAction(x);
            boolean stop = false;
            switch (act.getActionID()) {
                case ALL_IN:
                case CALL:
                case FOLD:
                case UNCALLED_BET:
                case CALL_ALL_IN:
                    hand.addAction(action, x + 1);
                    stop = true;
                    break;
            }
            if (stop) {
                break;
            }
		}

	}

	private static float getValue(String line) {
		line = line.trim();

		if (line.endsWith(ALL_IN)) {
			line = line.substring(0, line.length() - ALL_IN.length());
		}

		int index = line.lastIndexOf(' ') + 1;

		return Float.parseFloat(line.substring(index));
	}

	private static Player getClonedPlayer(String line, HashMap<String, Player> players, float value) {
		String name = getPlayerName(line);
        Player player =  players.get(name);
        player.decreaseStack(value);

		return player.clonePlayer();
	}

	private static String getPlayerName(String line) {
		int indexColon = line.lastIndexOf(':');

		return line.substring(0, indexColon);
	}

	private static String[] getCards(String line) {
		int index = line.lastIndexOf('[') + 1;
		int finalIndex = line.lastIndexOf(']');

		String cards = line.trim().substring(index, finalIndex);

		return cards.split(" ");
	}

	private static float getValueFromStreet(String name, Street street, ArrayList<Action> actions) {
		float totalValue = 0;

		int actionSize = actions.size();
		for (int x = actionSize - 1; x >= 0; x--) {
            Action action = actions.get(x);
			if (action.getStreet() != street) {
				break;
			}
			if (action.getPlayer().getName().equals(name) && action.getActionID() != ActionID.HOLE_CARDS) {
				totalValue = actions.get(x).getValue();
                break;
			}
		}

		return totalValue;
	}
}
