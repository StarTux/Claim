package com.winthier.claim;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Test;

public final class ClaimTest {
    private Random random = new Random(0);

    @Test
    public void main() {
        Claim claim = makeTestClaim();
        claim.getSubclaims().add(makeTestClaim());
        claim.getSubclaims().add(makeTestClaim());
        claim.getSubclaims().add(makeTestClaim());
        for (Claim subclaim: claim.getSubclaims()) subclaim.setSuperclaim(claim);
        Map serialized = claim.serialize();
        Claim claim2 = new Claim(serialized);
        Map serialized2 = claim2.serialize();
        Assert.assertTrue("(1) Claim contents differ", claim.contentEquals(claim2));
        Assert.assertEquals("(2) Serialized claims differ", serialized, serialized2);
        Assert.assertFalse("(3) Differing claims report same content", claim.contentEquals(makeTestClaim()));
        Assert.assertFalse("(4) Differing claims have equal serialized forms", serialized.equals(makeTestClaim().serialize()));
        for (Claim subclaim: claim.getSubclaims()) Assert.assertSame(subclaim.getSuperclaim(), claim);
        for (Claim subclaim: claim2.getSubclaims()) Assert.assertSame(subclaim.getSuperclaim(), claim2);
    }

    private Claim makeTestClaim() {
        Claim claim = new Claim(new UUID(random.nextLong(), random.nextLong()), "" + random.nextInt(), new Rectangle(random.nextInt(9999) - random.nextInt(9999), random.nextInt(9999) - random.nextInt(9999), random.nextInt(99) + 1, random.nextInt(99) + 1), System.currentTimeMillis());
        claim.getTrusted().put(new UUID(random.nextLong(), random.nextLong()), Trust.BUILD);
        claim.getTrusted().put(new UUID(random.nextLong(), random.nextLong()), Trust.USE);
        claim.getTrusted().put(new UUID(random.nextLong(), random.nextLong()), Trust.CHEST);
        if (random.nextBoolean()) claim.getOptions().put(Claim.Option.PVP, random.nextBoolean());
        if (random.nextBoolean()) claim.getOptions().put(Claim.Option.FIRE_SPREAD, random.nextBoolean());
        if (random.nextBoolean()) claim.getOptions().put(Claim.Option.TNT_DAMAGE, random.nextBoolean());
        if (random.nextBoolean()) claim.getOptions().put(Claim.Option.CREEPER_DAMAGE, random.nextBoolean());
        return claim;
    }
}
