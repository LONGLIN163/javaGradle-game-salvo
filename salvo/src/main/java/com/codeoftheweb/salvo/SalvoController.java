package com.codeoftheweb.salvo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class SalvoController {

    /*---------------------------Define a Method to Return JSON with Games Information------------------------------*/

    @Autowired
    private GameRepository gameRepository;
    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    private ShipRepository shipRepository;


    @RequestMapping("/games")
    public Map<String, Object> getAllGamesDetails(Authentication authentication) {
        //LinkedHashMap<String, Object> gamesDto = new LinkedHashMap<> ();
        Map<String, Object> gamesDto = new HashMap<> ();
        if (!isGuest (authentication)) {
            gamesDto.put ("player", makePlayerDto (getCurrentUser (authentication)));
        } else {
            gamesDto.put ("player", "stranger");
        }


        gamesDto.put ("games", gameRepository.findAll ()
                //return gameRepository.findAll ()

                //put array/list above in to the stream.iterate/loop in side stream
                .stream ()

                //use Lambda recive a function as parameter to get info from each game.
                //map every element result with method relevant.
                .map (game -> makeGameDTO (game))

                //Return a list from the result of above.
                .collect (Collectors.toList ()));

        return gamesDto;

    }

    //create a method to get the info about id and createData with type Map.
    private Map<String, Object> makeGameDTO(Game game) {

        //use map to get game info we want,and put them in to an obj,and return the Map obj.
        Map<String, Object> gameDto = new HashMap<> ();
        gameDto.put ("id", game.getId ());
        gameDto.put ("createDate", game.getDate ());


            gameDto.put ("gamePlayer", game.getGamePlayer ()
                    .stream ()
                    .sorted ((gp1, gp2) -> gp1.getId ().compareTo (gp2.getId ()))
                    //loop.receive each gamePlayer,map the return value from the method below.
                    .map (gamePlayer -> makeGamePlayerDTO (gamePlayer))
                    // collect all map(obj) ,then return a new list/array of obj.
                    .collect (Collectors.toList ()));

            gameDto.put ("scores", game.getGamePlayer ()
                    .stream ()
                    .filter (gp-> gp.getPlayer ().getScore (gp.getGame ()) != null)
                    .map (gp -> makeScoreDto (gp.getPlayer ().getScore (gp.getGame ())))
                    .collect (Collectors.toSet ()));

        return gameDto;
    }

    //create a method to get score's player's id and name,and get score,then put them in the map.
    private Map<String, Object> makeScoreDto(Score score) {
        Map<String, Object> scoreDto = new HashMap<> ();

            scoreDto.put ("player",score.getPlayer ().getUserName ());
            //scoreDto.put ("player",makePNdto (score));
            scoreDto.put ("player_id", score.getPlayer ().getId ());
            scoreDto.put ("scores", score.getScore ());

        return scoreDto;
    }


    //create a method to get gamePlayers' id and players,and put them in the map.
    private Map<String, Object> makeGamePlayerDTO(GamePlayer gamePlayer) {
        Map<String, Object> gamePlayerDto = new HashMap<> ();
        gamePlayerDto.put ("id", gamePlayer.getId ());

        //because in one game only have one gamePlayer(in other words,one player cant play with themself).so we dont need to loop them.
        gamePlayerDto.put ("player", makePlayerDto (gamePlayer.getPlayer ()));
        return gamePlayerDto;
    }

    //create a method to get player's id and name and put them in to map-playerDto.
    private Map<String, Object> makePlayerDto(Player player) {
        Map<String, Object> playerDto = new HashMap<> ();
        playerDto.put ("id", player.getId ());
        playerDto.put ("name", player.getUserName ());
        return playerDto;
    }

    /*----------------------Define a Method to Return JSON with GamePlayer Information--------------------------*/

    @Autowired
    private GamePlayerRepository gamePlayerRepository;

    @RequestMapping(path ="/game_view/{gpID}",method = RequestMethod.GET)
    //create the function return the Map of everything.through the Parameter gpID
    // public ResponseEntity LinkedHashMap<String, Object> getGameView(@PathVariable Long gpID,Authentication authentication) {

    public ResponseEntity <Map<String, Object>> getGameView(@PathVariable Long gpID,Authentication authentication) {

        //get one specific gameView url end with gamePlayer with one ID=1
        GamePlayer gamePlayer = gamePlayerRepository.getOne (gpID);

        Map<String, Object> someGameInfo = new HashMap<> ();
        if (gamePlayer.getPlayer ().getId ()!=getCurrentUser (authentication).getId ()) {
            return new ResponseEntity<> (responseDto ("error","u r not the ownner of this gamePlayer"), HttpStatus.FORBIDDEN);
        }else {
        someGameInfo.put ("id", gamePlayer.getGame ().getId ());
        someGameInfo.put ("createDate", gamePlayer.getGame ().getDate ());
        someGameInfo.put ("gamePlayers", gamePlayer.getGame ().getGamePlayer ()
                .stream ()
                .sorted ((gp1, gp2) -> gp1.getId ().compareTo (gp2.getId ()))
                .map (gp -> makeGamePlayerDTO (gp))
                .collect (Collectors.toList ()));

        //put the ships in the someGameInfo Map
        //loop the ships obj,get the each ship's shipType and Locations
        someGameInfo.put ("ships", gamePlayer.getShips ()
                .stream ()
                .map (ship -> makeShipDto (ship))
                .collect (Collectors.toList ()));

        someGameInfo.put ("salvos", gamePlayer.getGame ().getGamePlayer ()
                .stream ()
                .map (gp -> makeSalvoDto (gp))
                .collect (Collectors.toList ()));

            //return someGameInfo;
            return new ResponseEntity<> (responseDto ("success",someGameInfo), HttpStatus.CREATED);
        }

    }

    private Map<String, Map<String, Object>> makeSalvoDto(GamePlayer gamePlayer) {
        Map<String, Map<String, Object>> salvoDto = new HashMap<> ();
        for (Salvo salvo : gamePlayer.getSalvos ()) {
            LinkedHashMap<String, Object> eachSalvoDto = new LinkedHashMap<> ();
            eachSalvoDto.put (gamePlayer.getId ().toString (), salvo.getFireLocations ());
            salvoDto.put (String.valueOf (salvo.getTurn ()), eachSalvoDto);
        }
        return salvoDto;
    }

    //create shipDto obj contain type and location.
    private Map<String, Object> makeShipDto(Ship ship) {
        Map<String, Object> shipDto = new HashMap<> ();
        shipDto.put ("shipType", ship.getShipType ());
        shipDto.put ("locations", ship.getLocations ());
        return shipDto;
    }


    /*----------------------Define a Method to create a new player--------------------------*/
    @RequestMapping(path = "/players", method = RequestMethod.POST)
    public ResponseEntity<Map<String,Object>> register(
           @RequestBody Player player) {

        if (player.getUserName ().isEmpty () || player.getPassword ().isEmpty ()) {
            return new ResponseEntity<> (responseDto ("error","Missing data"), HttpStatus.NOT_FOUND);
        }

        else if (playerRepository.findByUserName (player.getUserName ()) != null) {
            return new ResponseEntity<> (responseDto ("error","Name already in use"), HttpStatus.FORBIDDEN);
        }

        else {
        playerRepository.save (new Player (player.getUserName (), passwordEncoder.encode (player.getPassword ())));
        return new ResponseEntity<> (responseDto ("success",player.getUserName ()),HttpStatus.CREATED);}
    }



//    /*----------------------Define a Method to create a new game--------------------------*/

    @RequestMapping(path = "/games", method = RequestMethod.POST)
    public ResponseEntity<Map<String,Object>> createNewGame(Authentication authentication) {

            if (isGuest (authentication)) {
            return new ResponseEntity<> (responseDto ("fail","You need to signup!!!"), HttpStatus.UNAUTHORIZED);
        } else {
            Game g1=new Game (new Date ());
            GamePlayer gp1=new GamePlayer (new Date (), g1, getCurrentUser (authentication));

           // g1.addGamePlayer (gp1);

            gameRepository.save (g1);

            gamePlayerRepository.save (gp1);
            return new ResponseEntity<> (responseDto("gp1",gp1.getId ()),HttpStatus.CREATED);}
    }


/*
    *//*----------------------Define a Method to join the game--------------------------*//*

    @RequestMapping(path = "/game/{gId}/players", method = RequestMethod.POST)
    public ResponseEntity<Map<String,Object>> joinNewGame(Authentication authentication,@PathVariable Long gId) {
        Game game=gameRepository.getOne (gId);
        //Game game=game.getGamePlayer ();
        if (isGuest (authentication)) {
            return new ResponseEntity<> (responseDto ("tip","You need to signUp!!!"), HttpStatus.UNAUTHORIZED);
        }
        else if(game.getId ()==null){
            return new ResponseEntity<> (responseDto ("erro","No such game!!!"), HttpStatus.FORBIDDEN);
        }
        else if(game.getGamePlayer ().size ()==2){
          return new ResponseEntity<> (responseDto ("hi","Game is full!!!"), HttpStatus.FORBIDDEN);
        } else {
            if(game.getGamePlayer().get(0).getPlayer ().equals (getCurrentUser (authentication))){
                return new ResponseEntity<> (responseDto ("forbidden","you've already in this game!!!"), HttpStatus.FORBIDDEN);
            }else {
            GamePlayer gp2=new GamePlayer (new Date (), game, getCurrentUser (authentication));
            gameRepository.save (game);
            gamePlayerRepository.save (gp2);
            return new ResponseEntity<> (responseDto("gp2Id",gp2.getId ()),HttpStatus.CREATED);}
        }
    }*/


    /*----------------------Define a Method to join the game--------------------------*/
    @RequestMapping(path = "/game/{gId}/players", method = RequestMethod.POST)
    public ResponseEntity<Map<String,Object>> joinNewGame(Authentication authentication,@PathVariable Long gId) {
        Game game=gameRepository.getOne (gId);
        List<GamePlayer> gamePlayers = new ArrayList<> ();
        gamePlayers.addAll (game.getGamePlayer ());
        if (isGuest (authentication)) {
            return new ResponseEntity<> (responseDto ("tip","You need to signUp!!!"), HttpStatus.UNAUTHORIZED);
        } else if(game.getGamePlayer ().size ()==2){
            return new ResponseEntity<> (responseDto ("hi","Game is full!!!"), HttpStatus.FORBIDDEN);
        } else if(game.getGamePlayer ().size ()==1 && gamePlayers.get(0).getPlayer ().equals (getCurrentUser (authentication))){
                return new ResponseEntity<> (responseDto ("forbidden","you've already in this game!!!"), HttpStatus.FORBIDDEN);
        }else {
            GamePlayer gp2=new GamePlayer (new Date (), game, getCurrentUser (authentication));
            gameRepository.save (game);
            gamePlayerRepository.save (gp2);
            return new ResponseEntity<> (responseDto("gp2Id",gp2.getId ()),HttpStatus.CREATED);
        }
    }

    /*-------------------------Define a Method to place ships--------------------------*/

    @RequestMapping(path = "/games/players/{gpId}/ships", method = RequestMethod.POST)
    public ResponseEntity<Map<String,Object>> placeShips(Authentication authentication,@PathVariable Long gpId,@RequestBody Set<Ship> ships) {
        GamePlayer gp=gamePlayerRepository.getOne (gpId);

        if (isGuest (authentication)) {
            return new ResponseEntity<> (responseDto ("No","You r no current user logged in!!!"), HttpStatus.UNAUTHORIZED);
        } else if(gp.getShips().containsAll (ships)){
            return new ResponseEntity<> (responseDto ("whoops","already has ships placed!!!"), HttpStatus.FORBIDDEN);
        } else {
            for(Ship ship : ships){
                ship.setGamePlayer (gp);
                shipRepository.save (ship);
            }
             return new ResponseEntity<> (responseDto ("success",gp.getShips ()),HttpStatus.CREATED);
        }
    }


    /*----------------------Create a responseDto method--------------------------*/
    private Map<String, Object> responseDto(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    /*----------------------Define Method to find current player info--------------------------*/

    private Player getCurrentUser(Authentication authentication) {
        return playerRepository.findByUserName (authentication.getName ());
    }

    private boolean isGuest(Authentication authentication) {
        return authentication == null || authentication instanceof AnonymousAuthenticationToken;
    }

}




//
//            else if(gp.getShips().size () > 0){
//                return new ResponseEntity<> (responseDto ("whoops","already has ships placed!!!"), HttpStatus.FORBIDDEN);
