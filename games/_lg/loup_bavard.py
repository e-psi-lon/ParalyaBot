
class LoupBavard:
    def __init__(self, player_id, bot):
        self.bot = bot
        self.player_id = player_id
        self.mots_places = 0
        self.mot_actuel = None
        self.mot_place = False

    def set_mot(self, mot):
        self.mot_actuel = mot
