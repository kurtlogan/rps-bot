package services

import com.google.inject.{ImplementedBy, Inject}
import models.{GameState, Move, Play, Result}
import models.Move.Move
import play.api.Logger
import play.api.cache.SyncCacheApi


@ImplementedBy(classOf[CGameStateService])
trait GameStateService {
  def saveState(gameState: GameState): Unit
  def getState(): GameState

  def addOurMove(move: Move): Unit
  def addOpponentMove(move: Move): Unit

  def setCurrentGuesstimater(n: Int): Unit
  def setLastUpdateGuesstimater(n: Int): Unit

  def getLastPlay(): Play
  def getPlays(): List[Play]

  def hasDynamite: Boolean
}

class CGameStateService @Inject() (cacheApi: SyncCacheApi) extends GameStateService {
  def saveState(gameState: GameState) = cacheApi.set(GameState.key, gameState)
  def getState() = cacheApi.get[GameState](GameState.key) match {
    case Some(gs) => gs
    case _ => throw new NoSuchElementException("Game state not in cache")
  }

  def addOurMove(move: Move): Unit = updateState { gs =>
    val dynaCount = if (move == Move.DYNAMITE) gs.dynamiteCount - 1 else gs.dynamiteCount

    gs.copy(plays = Play(move) :: gs.plays, dynamiteCount = dynaCount)
  }

  def addOpponentMove(move: Move): Unit = updateState { gameState =>
    val lastPlay = gameState.plays.head

    if (lastPlay.opponentMove.isDefined || lastPlay.result.isDefined)
      throw new NoSuchElementException("Unable to find last play")

    val newPlay = Play(lastPlay.ourMove, Some(move), Some(Result.fromPlay(lastPlay.ourMove, move)))
    val round = gameState.round + 1

    Logger.info(s"Begin round: $round")

    gameState.copy(round = round, plays = newPlay :: gameState.plays.tail)
  }

  def setCurrentGuesstimater(n: Int): Unit = updateState(_.copy(currentGuesstimater = n))
  def setLastUpdateGuesstimater(n: Int): Unit = updateState(_.copy(lastUpdateGuesstimater = n))

  def getLastPlay(): Play = getState().plays match {
      case x :: _ if x.opponentMove.isDefined && x.result.isDefined => x
      case _ :: y :: _ => y
      case _ => throw new NoSuchElementException("No last play")
    }

  def getPlays(): List[Play] = getState().plays

  def hasDynamite: Boolean = getState().dynamiteCount > 0

  private def updateState(fn: GameState => GameState) = {
    val gameState = getState()
    val updatedState = fn(gameState)
    saveState(updatedState)
  }
}