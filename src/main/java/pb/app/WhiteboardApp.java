package pb.app;

import pb.IndexServer;
import pb.WhiteboardServer;
import pb.managers.ClientManager;
import pb.managers.PeerManager;
import pb.managers.endpoint.Endpoint;
import pb.utils.Utils;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.*;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;


/**
 * Initial code obtained from:
 * https://www.ssaurel.com/blog/learn-how-to-make-a-swing-painting-and-drawing-application/
 */
public class WhiteboardApp {
	private static Logger log = Logger.getLogger(WhiteboardApp.class.getName());
	/**
	 * Emitted to another peer to subscribe to updates for the given board. Argument
	 * must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String listenBoard = "BOARD_LISTEN";

	/**
	 * Emitted to another peer to unsubscribe to updates for the given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String unlistenBoard = "BOARD_UNLISTEN";

	/**
	 * Emitted to another peer to get the entire board data for a given board.
	 * Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String getBoardData = "GET_BOARD_DATA";

	/**
	 * Emitted to another peer to give the entire board data for a given board.
	 * Argument must have format "host:port:boardid%version%PATHS".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardData = "BOARD_DATA";

	/**
	 * Emitted to another peer to add a path to a board managed by that peer.
	 * Argument must have format "host:port:boardid%version%PATH". The numeric value
	 * of version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathUpdate = "BOARD_PATH_UPDATE";

	/**
	 * Emitted to another peer to indicate a new path has been accepted. Argument
	 * must have format "host:port:boardid%version%PATH". The numeric value of
	 * version must be equal to the version of the board without the PATH added,
	 * i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardPathAccepted = "BOARD_PATH_ACCEPTED";

	/**
	 * Emitted to another peer to remove the last path on a board managed by that
	 * peer. Argument must have format "host:port:boardid%version%". The numeric
	 * value of version must be equal to the version of the board without the undo
	 * applied, i.e. the current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoUpdate = "BOARD_UNDO_UPDATE";

	/**
	 * Emitted to another peer to indicate an undo has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the undo applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardUndoAccepted = "BOARD_UNDO_ACCEPTED";

	/**
	 * Emitted to another peer to clear a board managed by that peer. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearUpdate = "BOARD_CLEAR_UPDATE";

	/**
	 * Emitted to another peer to indicate an clear has been accepted. Argument must
	 * have format "host:port:boardid%version%". The numeric value of version must
	 * be equal to the version of the board without the clear applied, i.e. the
	 * current version of the board.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardClearAccepted = "BOARD_CLEAR_ACCEPTED";

	/**
	 * Emitted to another peer to indicate a board no longer exists and should be
	 * deleted. Argument must have format "host:port:boardid".
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardDeleted = "BOARD_DELETED";

	/**
	 * Emitted to another peer to indicate an error has occurred.
	 * <ul>
	 * <li>{@code args[0] instanceof String}</li>
	 * </ul>
	 */
	public static final String boardError = "BOARD_ERROR";

	/**
	 * White board map from board name to board object
	 */
	Map<String,Whiteboard> whiteboards;

	/**
	 * The currently selected white board
	 */
	Whiteboard selectedBoard = null;

	/**
	 * The peer:port string of the peer. This is synonomous with IP:port, host:port,
	 * etc. where it may appear in comments.
	 */
	String peerport="standalone"; // a default value for the non-distributed version

	Map<String, Endpoint> endpoints;
	Map<String, Set<Endpoint>> endpointListen;

	/*
	 * GUI objects, you probably don't need to modify these things... you don't
	 * need to modify these things... don't modify these things [LOTR reference?].
	 */

	JButton clearBtn, blackBtn, redBtn, createBoardBtn, deleteBoardBtn, undoBtn;
	JCheckBox sharedCheckbox ;
	DrawArea drawArea;
	JComboBox<String> boardComboBox;
	boolean modifyingComboBox=false;
	boolean modifyingCheckBox=false;

	/**
	 * Initialize the white board app.
	 */
	public WhiteboardApp(int peerPort,String whiteboardServerHost,
						 int whiteboardServerPort) {
		whiteboards = new HashMap<>();
		endpoints = new HashMap<>();
		endpointListen = new HashMap<>();
		this.peerport = String.format("%s:%d", whiteboardServerHost, peerPort);
		PeerManager peerManager = new PeerManager(peerPort);
		show(peerport);
		try {
			connectToServer(peerPort, peerManager, whiteboardServerHost, whiteboardServerPort);
		} catch (UnknownHostException unknownHostException) {
			unknownHostException.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/******
	 *
	 * Utility methods to extract fields from argument strings.
	 *
	 ******/

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return peer:port:boardid
	 */
	public static String getBoardName(String data) {
		String[] parts=data.split("%",2);
		return parts[0];
	}

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return boardid%version%PATHS
	 */
	public static String getBoardIdAndData(String data) {
		String[] parts=data.split(":");
		return parts[2];
	}

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return version%PATHS
	 */
	public static String getBoardData(String data) {
		String[] parts=data.split("%",2);
		return parts[1];
	}

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return version
	 */
	public static long getBoardVersion(String data) {
		String[] parts=data.split("%",3);
		return Long.parseLong(parts[1]);
	}

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return PATHS
	 */
	public static String getBoardPaths(String data) {
		String[] parts=data.split("%",3);
		return parts[2];
	}

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return peer
	 */
	public static String getIP(String data) {
		String[] parts=data.split(":");
		return parts[0];
	}

	/**
	 *
	 * @param data = peer:port:boardid%version%PATHS
	 * @return port
	 */
	public static int getPort(String data) {
		String[] parts=data.split(":");
		return Integer.parseInt(parts[1]);
	}

	/******
	 *
	 * Methods called from events.
	 *
	 ******/

	// From whiteboard server
	public void connectToServer(int peerPort, PeerManager peerManager, String whiteboardServerHost,
								int whiteboardServerPort) throws UnknownHostException, InterruptedException {
		// connect to the whiteboard server and receive the sharing board
		ClientManager clientManager = peerManager.connect(whiteboardServerPort, whiteboardServerHost);
		clientManager.on(PeerManager.peerStarted, (args)-> {
			Endpoint endpoint = (Endpoint)args[0];
			endpoints.put("server", endpoint);
			System.out.println("Connected to whiteboard server: "+endpoint.getOtherEndpointId());
			endpoint.on(WhiteboardServer.sharingBoard, (args2)-> {
				String boardname = (String) args2[0];
				if (getPort(boardname) == peerPort) {
					System.out.println("This is the local board: "+ boardname);
					try {
						shareBoard(peerManager, boardname);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("Receive the board: "+ boardname);
					try {
						getBoard(peerManager, boardname);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}).on(WhiteboardServer.unsharingBoard, (args2)->{
				String boardname = (String) args2[0];
				if (getPort(boardname) == peerPort) {
					System.out.println("This is the local board: "+ boardname);
				} else {
					System.out.println("Receive the unsharing board: "+ boardname);
					deleteBoard(boardname);
				}
			});
		}).on(PeerManager.peerStopped, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Disconnected from the whiteboard server: "+endpoint.getOtherEndpointId());
		}).on(PeerManager.peerError, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("There was an error communicating with the whiteboard server: "
					+endpoint.getOtherEndpointId());
		});
		clientManager.start();
		clientManager.join();
		peerManager.joinWithClientManagers();
	}

	// From whiteboard peer
	public void getBoard(PeerManager peerManager, String data) throws InterruptedException {

		ClientManager clientManager;
		try {
			clientManager = peerManager.connect(getPort(data), getIP(data));
		} catch (NumberFormatException e) {
			System.out.println("Port is not Number" + getPort(data));
			return;
		} catch (UnknownHostException e) {
			System.out.println("Could not find peer IP address" + getIP(data));
			return;
		}
		clientManager.on(PeerManager.peerStarted, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Connected to other peer: "+endpoint.getOtherEndpointId());
			endpoints.put(getBoardName(data), endpoint);
			Whiteboard whiteboard = new Whiteboard(getBoardName(data), true);
			addBoard(whiteboard, false);
			endpoint.on(boardData, (args2)->{
				String boarddata = (String)args2[0];
				whiteboard.whiteboardFromString(getBoardName(boarddata), getBoardData(boarddata));
				drawSelectedWhiteboard();
				endpoints.get(getBoardName(boarddata)).emit(listenBoard, getBoardName(boarddata));
			}).on(boardError, (args2)-> {
				System.out.println("Board Error");
				clientManager.shutdown();
			}).on(boardPathUpdate, (args2)->{
				String boardpathupdate = (String)args2[0];
				if (getBoardVersion(boardpathupdate) != selectedBoard.getVersion() &&
						selectedBoard.getName().equals(getBoardName(boardpathupdate))) {
					String[] paths = getBoardPaths(boardpathupdate).split("%");
					WhiteboardPath whiteboardPath = new WhiteboardPath(paths[paths.length-1]);
					pathCreatedLocally(whiteboardPath);
				}
			}).on(boardClearUpdate, (args2)->{
				String clear = (String)args2[0];
				if (getBoardVersion(clear) != getBoardVersion(selectedBoard.toString()) &&
						selectedBoard.getName().equals(getBoardName(clear))) {
					clearedLocally();
					System.out.println("Clear locally");
				}
			}).on(boardUndoUpdate, (args2)->{
				String undo = (String)args2[0];
				if (getBoardVersion(undo) != getBoardVersion(selectedBoard.toString()) &&
						selectedBoard.getName().equals(getBoardName(undo))) {
					undoLocally();
					System.out.println("Undo locally");
				}
			}).on(boardDeleted, (args2)->{
				String delete = (String)args2[0];
				deleteBoard(delete);
			});
		}).on(PeerManager.peerStopped, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Disconnected from the whiteboard server: "+endpoint.getOtherEndpointId());
		}).on(PeerManager.peerError, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("There was an error communicating with the whiteboard server: "
					+endpoint.getOtherEndpointId());
		});
		clientManager.start();
	}


	// From whiteboard peer
	public void shareBoard(PeerManager peerManager, String data) throws InterruptedException {
		peerManager.on(PeerManager.peerStarted, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Connected from peer: "+endpoint.getOtherEndpointId());
			endpoints.put(getBoardName(data), endpoint);
			if (!endpointListen.containsKey(getBoardName(data))) {
				Set<Endpoint> inti = new HashSet<>();
				endpointListen.put(getBoardName(data), inti);
			}
			endpoint.on(getBoardData, (args2)-> {
				String otherName = (String)args2[0];
				System.out.println("Send data to" + otherName);
				endpoints.put(getBoardName(otherName), endpoint);
				endpoints.get(getBoardName(otherName)).emit(boardData, whiteboards.get(getBoardName(otherName)).toString());
			}).on(listenBoard, (args2)->{
				String listenboard = (String)args2[0];
				if (!endpointListen.containsKey(getBoardName(listenboard))) {
					Set<Endpoint> endlisten = new HashSet<>();
					endlisten.add(endpoints.get(getBoardName(listenboard)));
					endpointListen.put(getBoardName(listenboard), endlisten);
				} else if (!endpointListen.get(getBoardName(listenboard)).contains(endpoints.get(getBoardName(listenboard)))) {
					Set<Endpoint> endlisten = endpointListen.get(getBoardName(listenboard));
					endlisten.add(endpoints.get(getBoardName(listenboard)));
					endpointListen.replace(getBoardName(listenboard), endlisten);
				}
				System.out.println("add listen board");
			}).on(unlistenBoard, (args2)->{
				String unlistenboard = (String)args2[0];
				if (endpointListen.containsKey(getBoardName(unlistenboard)) &&
						(endpointListen.get(getBoardName(unlistenboard)).contains(endpoints.get(getBoardName(unlistenboard))))) {
					Set<Endpoint> endlisten = endpointListen.get(getBoardName(unlistenboard));
					endlisten.remove(endpoints.get(getBoardName(unlistenboard)));
					endpointListen.replace(getBoardName(unlistenboard), endlisten);
				}
				System.out.println("remove unlisten board");
			}).on(boardPathAccepted, (args2)->{
				String path = (String)args2[0];
				if (getBoardVersion(path) != whiteboards.get(getBoardName(path)).getVersion()) {
					String[] paths = getBoardPaths(path).split("%");
					WhiteboardPath whiteboardPath = new WhiteboardPath(paths[paths.length-1]);
					if (selectedBoard.getName().equals(getBoardName(path))) {
						pathCreatedLocally(whiteboardPath);
					} else {
						Whiteboard whiteboard = whiteboards.get(getBoardName(path));
						whiteboard.addPath(whiteboardPath, whiteboards.get(getBoardName(path)).getVersion());
						whiteboards.replace(getBoardName(path), whiteboard);
					}

					for (Endpoint endpoint1: endpointListen.get(getBoardName(path))) {
						endpoint1.emit(boardPathUpdate, path);
					}
				}
				System.out.println("path update already accepted " + path);
			}).on(boardClearAccepted, (args2)->{
				String clear = (String)args2[0];
				if (getBoardVersion(clear) != whiteboards.get(getBoardName(clear)).getVersion()) {
					if (selectedBoard.getName().equals(getBoardName(clear))) {
						clearedLocally();
					} else {
						Whiteboard whiteboard = whiteboards.get(getBoardName(clear));
						whiteboard.undo(whiteboards.get(getBoardName(clear)).getVersion());
						whiteboards.replace(getBoardName(clear), whiteboard);
					}
					for (Endpoint endpoint1: endpointListen.get(getBoardName(clear))) {
						endpoint1.emit(boardClearUpdate, clear);
					}
				}
				System.out.println("board clear already accepted " + clear);
			}).on(boardUndoAccepted, (args2)->{
				String undo= (String)args2[0];
				if (getBoardVersion(undo) != whiteboards.get(getBoardName(undo)).getVersion()) {
					if (selectedBoard.getName().equals(getBoardName(undo))) {
						undoLocally();
					} else {
						Whiteboard whiteboard = whiteboards.get(getBoardName(undo));
						whiteboard.undo(whiteboards.get(getBoardName(undo)).getVersion());
						whiteboards.replace(getBoardName(undo), whiteboard);
					}
					for (Endpoint endpoint1: endpointListen.get(getBoardName(undo))) {
						endpoint1.emit(boardUndoUpdate, undo);
					}
				}
				System.out.println("board undo already accepted " + undo);
			});
		}).on(PeerManager.peerStopped, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("Disconnected from the whiteboard server: "+endpoint.getOtherEndpointId());
		}).on(PeerManager.peerError, (args)->{
			Endpoint endpoint = (Endpoint)args[0];
			System.out.println("There was an error communicating with the whiteboard server: "
					+endpoint.getOtherEndpointId());
		});

		if (!peerManager.getState().equals(Thread.State.TERMINATED)) {
			peerManager.start();
		}
		//peerManager.shutdown();
	}



	/******
	 *
	 * Methods to manipulate data locally. Distributed systems related code has been
	 * cut from these methods.
	 *
	 ******/

	/**
	 * Wait for the peer manager to finish all threads.
	 */
	public void waitToFinish() {
	}

	/**
	 * Add a board to the list that the user can select from. If select is
	 * true then also select this board.
	 * @param whiteboard
	 * @param select
	 */
	public void addBoard(Whiteboard whiteboard,boolean select) {
		synchronized(whiteboards) {
			whiteboards.put(whiteboard.getName(), whiteboard);
		}
		updateComboBox(select?whiteboard.getName():null);
	}

	/**
	 * Delete a board from the list.
	 * @param boardname must have the form peer:port:boardid
	 */
	public void deleteBoard(String boardname) {
		synchronized(whiteboards) {
			Whiteboard whiteboard = whiteboards.get(boardname);
			if(whiteboard!=null) {
				whiteboards.remove(boardname);
			}
		}
		updateComboBox(null);
	}

	/**
	 * Create a new local board with name peer:port:boardid.
	 * The boardid includes the time stamp that the board was created at.
	 */
	public void createBoard() {
		String name = peerport+":board"+Instant.now().toEpochMilli();
		Whiteboard whiteboard = new Whiteboard(name,false);
		addBoard(whiteboard,true);
	}

	/**
	 * Add a path to the selected board. The path has already
	 * been drawn on the draw area; so if it can't be accepted then
	 * the board needs to be redrawn without it.
	 * @param currentPath
	 */
	public void pathCreatedLocally(WhiteboardPath currentPath) {
		if(selectedBoard!=null) {
			if(!selectedBoard.addPath(currentPath,selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard(); // just redraw the screen without the path
			} else {
				// was accepted locally, so do remote stuff if needed
				drawSelectedWhiteboard();
				if (selectedBoard.isRemote()) {
					endpoints.get(selectedBoard.getName()).emit(boardPathAccepted, selectedBoard.toString());
				} else if (selectedBoard.isShared()) {
					for (Endpoint e1: endpointListen.get(selectedBoard.getName())) {
						e1.emit(boardPathUpdate, selectedBoard.toString());
					}
				}
			}
		} else {
			log.severe("path created without a selected board: "+currentPath);
		}
	}

	/**
	 * Clear the selected whiteboard.
	 */
	public void clearedLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.clear(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				if (selectedBoard.isRemote()) {
					endpoints.get(selectedBoard.getName()).emit(boardClearAccepted, selectedBoard.toString());
				} else if (selectedBoard.isShared()) {
					for (Endpoint e2: endpointListen.get(selectedBoard.getName())) {
						e2.emit(boardClearUpdate, selectedBoard.toString());
					}
				}
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("cleared without a selected board");
		}
	}

	/**
	 * Undo the last path of the selected whiteboard.
	 */
	public void undoLocally() {
		if(selectedBoard!=null) {
			if(!selectedBoard.undo(selectedBoard.getVersion())) {
				// some other peer modified the board in between
				drawSelectedWhiteboard();
			} else {
				if (selectedBoard.isRemote()) {
					endpoints.get(selectedBoard.getName()).emit(boardUndoAccepted, selectedBoard.toString());
				} else if (selectedBoard.isShared()) {
					for (Endpoint e3: endpointListen.get(selectedBoard.getName())) {
						e3.emit(boardUndoUpdate, selectedBoard.toString());
					}
				}
				drawSelectedWhiteboard();
			}
		} else {
			log.severe("undo without a selected board");
		}
	}

	/**
	 * The variable selectedBoard has been set.
	 */
	public void selectedABoard() {
		drawSelectedWhiteboard();
		log.info("selected board: "+selectedBoard.getName());
	}

	/**
	 * Set the share status on the selected board.
	 */
	public void setShare(boolean share) {
		if(selectedBoard!=null) {
			selectedBoard.setShared(share);
		} else {
			log.severe("there is no selected board");
		}
	}

	/**
	 * Called by the gui when the user closes the app.
	 */
	public void guiShutdown() {
		// do some final cleanup
		HashSet<Whiteboard> existingBoards= new HashSet<>(whiteboards.values());
		existingBoards.forEach((board)->{
			deleteBoard(board.getName());
			if (board.isShared()) {
				endpoints.get("server").emit(WhiteboardServer.unshareBoard, board.getName());
			} else if (board.isRemote()) {
				endpoints.get(board.getName()).emit(unlistenBoard, board.getName());
			}
		});
		whiteboards.values().forEach((whiteboard)->{

		});
		Utils.getInstance().cleanUp();
		System.exit(0);

	}

	/******
	 *
	 * GUI methods and callbacks from GUI for user actions.
	 * You probably do not need to modify anything below here.
	 *
	 ******/

	/**
	 * Redraw the screen with the selected board
	 */
	public void drawSelectedWhiteboard() {
		drawArea.clear();
		if(selectedBoard!=null) {
			selectedBoard.draw(drawArea);
		}
	}

	/**
	 * Setup the Swing components and start the Swing thread, given the
	 * peer's specific information, i.e. peer:port string.
	 */
	public void show(String peerport) {
		// create main frame
		JFrame frame = new JFrame("Whiteboard Peer: "+peerport);
		Container content = frame.getContentPane();
		// set layout on content pane
		content.setLayout(new BorderLayout());
		// create draw area
		drawArea = new DrawArea(this);

		// add to content pane
		content.add(drawArea, BorderLayout.CENTER);

		// create controls to apply colors and call clear feature
		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

		/**
		 * Action listener is called by the GUI thread.
		 */
		ActionListener actionListener = new ActionListener() {

			public void actionPerformed(ActionEvent e) {
				if (e.getSource() == clearBtn) {
					clearedLocally();
				} else if (e.getSource() == blackBtn) {
					drawArea.setColor(Color.black);
				} else if (e.getSource() == redBtn) {
					drawArea.setColor(Color.red);
				} else if (e.getSource() == boardComboBox) {
					if(modifyingComboBox) return;
					if(boardComboBox.getSelectedIndex()==-1) return;
					String selectedBoardName=(String) boardComboBox.getSelectedItem();

					if(whiteboards.get(selectedBoardName)==null) {
						log.severe("selected a board that does not exist: "+selectedBoardName);
						return;
					}

					if (selectedBoard != null && selectedBoard.isRemote()) {
						endpoints.get(selectedBoard.getName()).emit(unlistenBoard, selectedBoard.toString());
					}

					selectedBoard = whiteboards.get(selectedBoardName);
					// remote boards can't have their shared status modified
					if(selectedBoard.isRemote()) {
						sharedCheckbox.setEnabled(false);
						sharedCheckbox.setVisible(false);
						endpoints.get(selectedBoardName).emit(getBoardData, selectedBoard.toString());
					} else {
						modifyingCheckBox=true;
						sharedCheckbox.setSelected(selectedBoard.isShared());
						modifyingCheckBox=false;
						sharedCheckbox.setEnabled(true);
						sharedCheckbox.setVisible(true);
					}
					selectedABoard();
				} else if (e.getSource() == createBoardBtn) {
					createBoard();
				} else if (e.getSource() == undoBtn) {
					if(selectedBoard==null) {
						log.severe("there is no selected board to undo");
						return;
					}
					undoLocally();
				} else if (e.getSource() == deleteBoardBtn) {
					if(selectedBoard==null) {
						log.severe("there is no selected board to delete");
						return;
					}
					if (selectedBoard.isShared()) {
						Set<Endpoint> endlis =endpointListen.get(selectedBoard.getName());
						if (endlis != null) {
							for (Endpoint e4: endlis) {
								e4.emit(boardDeleted, selectedBoard.getName());
							}
							endpointListen.remove(selectedBoard.getName());
						}
					} else if(selectedBoard.isRemote()) {
						endpoints.get(selectedBoard.getName()).emit(unlistenBoard, selectedBoard.getName());
					}
					deleteBoard(selectedBoard.getName());
				}
			}
		};

		clearBtn = new JButton("Clear Board");
		clearBtn.addActionListener(actionListener);
		clearBtn.setToolTipText("Clear the current board - clears remote copies as well");
		clearBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		blackBtn = new JButton("Black");
		blackBtn.addActionListener(actionListener);
		blackBtn.setToolTipText("Draw with black pen");
		blackBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		redBtn = new JButton("Red");
		redBtn.addActionListener(actionListener);
		redBtn.setToolTipText("Draw with red pen");
		redBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		deleteBoardBtn = new JButton("Delete Board");
		deleteBoardBtn.addActionListener(actionListener);
		deleteBoardBtn.setToolTipText("Delete the current board - only deletes the board locally");
		deleteBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		createBoardBtn = new JButton("New Board");
		createBoardBtn.addActionListener(actionListener);
		createBoardBtn.setToolTipText("Create a new board - creates it locally and not shared by default");
		createBoardBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		undoBtn = new JButton("Undo");
		undoBtn.addActionListener(actionListener);
		undoBtn.setToolTipText("Remove the last path drawn on the board - triggers an undo on remote copies as well");
		undoBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		sharedCheckbox = new JCheckBox("Shared");
		sharedCheckbox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				if(!modifyingCheckBox) setShare(e.getStateChange()==1);
				if (e.getStateChange()==1){
					endpoints.get("server").emit(WhiteboardServer.shareBoard,selectedBoard.getName());
				} else {
					endpoints.get("server").emit(WhiteboardServer.unshareBoard,selectedBoard.getName());
				}
			}
		});
		sharedCheckbox.setToolTipText("Toggle whether the board is shared or not - tells the whiteboard server");
		sharedCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);


		// create a drop list for boards to select from
		JPanel controlsNorth = new JPanel();
		boardComboBox = new JComboBox<String>();
		boardComboBox.addActionListener(actionListener);


		// add to panel
		controlsNorth.add(boardComboBox);
		controls.add(sharedCheckbox);
		controls.add(createBoardBtn);
		controls.add(deleteBoardBtn);
		controls.add(blackBtn);
		controls.add(redBtn);
		controls.add(undoBtn);
		controls.add(clearBtn);

		// add to content pane
		content.add(controls, BorderLayout.WEST);
		content.add(controlsNorth,BorderLayout.NORTH);

		frame.setSize(600, 600);

		// create an initial board
		createBoard();

		// closing the application
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent windowEvent) {
				if (JOptionPane.showConfirmDialog(frame,
						"Are you sure you want to close this window?", "Close Window?",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION)
				{
					guiShutdown();
					frame.dispose();
				}
			}
		});

		// show the swing paint result
		frame.setVisible(true);

	}

	/**
	 * Update the GUI's list of boards. Note that this method needs to update data
	 * that the GUI is using, which should only be done on the GUI's thread, which
	 * is why invoke later is used.
	 *
	 * @param select, board to select when list is modified or null for default
	 *                selection
	 */
	private void updateComboBox(String select) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				modifyingComboBox=true;
				boardComboBox.removeAllItems();
				int anIndex=-1;
				synchronized(whiteboards) {
					ArrayList<String> boards = new ArrayList<String>(whiteboards.keySet());
					Collections.sort(boards);
					for(int i=0;i<boards.size();i++) {
						String boardname=boards.get(i);
						boardComboBox.addItem(boardname);
						if(select!=null && select.equals(boardname)) {
							anIndex=i;
						} else if(anIndex==-1 && selectedBoard!=null &&
								selectedBoard.getName().equals(boardname)) {
							anIndex=i;
						}
					}
				}
				modifyingComboBox=false;
				if(anIndex!=-1) {
					boardComboBox.setSelectedIndex(anIndex);
				} else {
					if(whiteboards.size()>0) {
						boardComboBox.setSelectedIndex(0);
					} else {
						drawArea.clear();
						createBoard();
					}
				}

			}
		});
	}

}
